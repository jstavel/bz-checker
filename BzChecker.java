package com.redhat.qe.auto.testng;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.apache.xmlrpc.XmlRpcException;

import testopia.API.Session;
import testopia.API.TestopiaObject;

/**
 * Example code to retrieve a bugzilla bug's status, given its ID.  This is for future use with testng, 
 * so that testng can decide whether to execute a test, based on the group annotation (which may contain
 * a bug id), and the status of that bug.  If the status is ON_QA, for example, it can be tested.
 * @author weissj
 *
 */
public class BzChecker {	
	
	public enum bzState { NEW, ASSIGNED, MODIFIED, ON_DEV, ON_QA, VERIFIED, FAILS_QA, RELEASE_PENDING, POST, CLOSED };
	
	protected static Logger log = Logger.getLogger(BzChecker.class.getName());
	protected Bug bug;
	public BzChecker() {
		
	}
	
	public void init() {
		bug = new Bug();
		try {
			bug.connectBZ();
		}catch(Exception e){
			throw new RuntimeException("Could not initialize BzChecker." ,e);
		}
	}
	
	public bzState getBugState(String bugId) {
		return bzState.valueOf(getBugField(bugId, "bug_status").toString());
	}
	
	public Object getBugField(String bugId, String fieldId){
		Object[] bugs = null;
		try {
			bugs = bug.getBugs("ids", new Object[] {bugId});
		}catch(Exception e){
			throw new RuntimeException("Could not retrieve bug " + bugId + " from bugzilla." ,e);
		}
		log.finer("Retrieved bugs: " + Arrays.deepToString(bugs));
		if (bugs.length ==0) throw new IllegalStateException("No bug found matching ID " + bugId);
		else if (bugs.length > 1) throw new IllegalStateException("Multiple matches found for bug ID " + bugId);
		else {
			Object thisbug = bugs[0];
			Map bmap = (Map)thisbug;
			
			log.finer("Found bug: " + thisbug.toString() );
			Map internals = (Map)bmap.get("internals");
			Object fieldValue = internals.get(fieldId);
			log.finer("Bug field " + fieldId + " of " + bugId + " is " + fieldValue.toString());
			return fieldValue;
		}
	}
	
	public void setBugState(String bugId, bzState state) {
		try {
			bug.update_bug_status(bugId, state);
		}
		catch(Exception e){
			throw new RuntimeException("Could not set bug status " + bugId + " in bugzilla." ,e);
		}
	}
	public void login(String userid, String password) {
		try {
			bug.login(userid, password);
		}
		catch(Exception e){
			throw new RuntimeException("Could not log in to bugzilla as " + userid ,e);
		}
	}
	
	public void addComment(String bugId, String comment){
		try {
			bug.add_bug_comment(bugId, comment);
		}
		catch(Exception e){
			throw new RuntimeException("Could not add comment to bug " + bugId ,e);
		}	
	}
	
	public void addKeywords(String bugId, String... keywords){
		editKeywords(bugId, true, keywords);
	}
	public void deleteKeywords(String bugId, String... keywords){
		editKeywords(bugId, true, keywords);
	}
	
	protected void editKeywords(String bugId, boolean add, String... keywords){
		try {
			Map<String,Object> updates = new HashMap<String,Object>();
			updates.put(add?"add_keyword":"delete_keyword", keywords);
			bug.update_bug(bugId, updates);
		}
		catch(Exception e){
			throw new RuntimeException("Could not " + (add? "add":"remove") + " keywords for bug " + bugId ,e);
		}	
	}
	
	public class Bug extends TestopiaObject{
		private String BZ_URL;
		//private StringAttribute bug_status = newStringAttribute("bug_status", null);
		
		public Bug(){
			listMethod = "Bug.get_bugs";
			System.setProperty("bugzilla.url", "https://bugzilla.redhat.com/bugzilla/xmlrpc.cgi");
			//System.setProperty("bugzilla.url", "https://bz-web2-test.devel.redhat.com/bugzilla/xmlrpc.cgi");
		}
		
		protected void connectBZ() throws XmlRpcException, GeneralSecurityException, IOException{
			BZ_URL = System.getProperty("bugzilla.url");
			session = new Session(null, null, new URL(BZ_URL));
			try {
				session.init();
			}
			catch(Exception e){
				log.log(Level.FINE, "Couldn't set up bugzilla connection.", e);
			}
		}
		
		public int login(String userid, String password) throws XmlRpcException{
			Map<String,Object> main = new HashMap<String,Object>();
			main.put("login", userid);
			main.put("password", password);
			Map map = (Map) this.callXmlrpcMethod("User.login", main);
			return (Integer)map.get("id");
		}
		
		/*
		 * Returns a Map containing an Array of Maps.  Within the innermost Maps (which represent bugs), there's another
		 * Map under the key "internals", which has a key "bug_status".  ugh.
		 */
		public Object[] getBugs(Map<String, Object> values) throws XmlRpcException
		{
			//some Testopia objects have no listing mechanism
			if(listMethod == null)
				return null;
			
			Map map = (Map) this.callXmlrpcMethod(listMethod, values);
			return (Object[])map.get("bugs");
			//return result;
		}
	
		public Object[] getBugs(String name, Object value) throws XmlRpcException {
			Map<String, Object> map = new HashMap<String, Object>();
			map.put(name, value);
			return getBugs(map);
		}
		
		
		public Map update_bug_status(String bug_id, bzState newState) throws XmlRpcException{
			Map<String,Object> updates = new HashMap<String,Object>();
			updates.put("bug_status", newState.toString());
			return update_bug(bug_id, updates);
		}
		
		protected Map update_bug(String bug_id, Map<String,Object> updates)throws XmlRpcException{
			Map<String,Object> main = new HashMap<String,Object>(); 
			main.put("updates", updates);
			main.put("ids", Integer.parseInt(bug_id));
			Map map = (Map) this.callXmlrpcMethod("Bug.update", main);
			
			System.out.println(map);
			return map;
		}
		
		public Map add_bug_comment(String bug_id, String comment) throws XmlRpcException{
			Map<String,Object> main = new HashMap<String,Object>(); 

			main.put("id", Integer.parseInt(bug_id));
			main.put("comment", comment);
			Map map = (Map) this.callXmlrpcMethod("Bug.add_comment", main);
			//Map map = (Map) this.callXmlrpcMethod("bug.add_comment", Integer.parseInt(bug_id), comment);
			
			System.out.println(map);
			return map;
		}
		
	}
	
	public static void main(String[] args) throws Exception{
		try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("/home/weissj/workspace/automatjon/jon-2.0/log.properties"));
		}catch(Exception e){
			System.err.println("Unable to read log config file.");
		}
		/*Bug myBug = new BzChecker().new Bug();
		//List<>
		myBug.connectBZ();
		List<String> ids = new ArrayList<String>();
		ids.add("497793");
		Object[] bugs = myBug.getBugs("ids",ids);
		for( Object bug: bugs){
			Map bmap = (Map)bug;
			
			log.info("Found bug: " + bug.toString() );
			Map internals = (Map)bmap.get("internals");
			log.info("Bug status is " + internals.get("bug_status"));
		}*/
		BzChecker checker = new BzChecker();
		checker.init();
		//String id = "497793";
		//log.info("State of " + id + " is " + checker.getBugState(id));
		//checker.login("jweiss@redhat.com", System.getProperty("bugzilla.password"));
		//checker.addComment("470058", "test comment");
		//checker.setBugState("470058", bzState.ON_QA);
		//checker.addKeywords("470058", "AutoVerified");
		log.info("Keywords: " + checker.getBugField("470058", "keywords"));
		
	}

}
