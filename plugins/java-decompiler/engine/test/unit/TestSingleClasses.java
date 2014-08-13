package unit;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;

import org.junit.Test;

import de.fernflower.main.decompiler.ConsoleDecompiler;
import de.fernflower.main.extern.IFernflowerPreferences;

public class TestSingleClasses {

	@Test
	public void test() throws IOException {

		Date start = new Date();
		
		String current_path = new File(".").getCanonicalPath().toString();

		iterateDirectory(new File(current_path + "/bin/unit/classes/"));
		
		System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
	}
	
	private void iterateDirectory(File dir) throws IOException {
		
    	for (File file : dir.listFiles()) {
    		if (file.isDirectory()) {
    			iterateDirectory(file);
    		} else if(file.getName().endsWith(".class")) {
    			decompileAndCheckFile(file);
    		}
    	}
	}
	
	private void decompileAndCheckFile(File file) throws IOException {

		try {
    		
    		ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{
    			put("log", "warn");
    			put("ren", "1");
    	        put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "1");
    	        put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "0");
    	        put(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION, "1");
    	        put(IFernflowerPreferences.LAMBDA_TO_ANONYMOUS_CLASS, "0");
    	        put(IFernflowerPreferences.USE_DEBUG_VARNAMES, "0");
    	        put(IFernflowerPreferences.NEW_LINE_SEPARATOR, "0");
    	     }});
    		
    		decompiler.addSpace(file, true);

    		// files 
    		String current_path = new File(".").getCanonicalPath().toString();
    		
    		String file_class_name = file.getName();
    		String file_name = file_class_name.substring(0, file_class_name.lastIndexOf(".class")); 
    		String file_java_name = file_name+".java"; 

    		File reference_file = new File(current_path + "/test/unit/results/" + file_name + ".dec");  
    		if(!reference_file.exists()) {
    			return; // no reference file for some reason, not yet created 
    		}
    		
    		File temp_dir = new File(Files.createTempDirectory("tempdec_"+file_name).toString());
    	 	
    		// decompile it
    		decompiler.decompileContext(temp_dir);
    		
    		// get both the decompiled file content and the reference
    		// NOTE: reference files are saved with Windows-style line endings. Convert them if you are decompiling to Unix.
    		File decompiled_file = new File(temp_dir, file_java_name);
    		String decompiled_content = new String(Files.readAllBytes(decompiled_file.toPath()), "UTF-8");
    		String reference_content = new String(Files.readAllBytes(reference_file.toPath()), "UTF-8");
    		
    		// clean up
    		decompiled_file.delete();
    		temp_dir.delete();

    		// compare file content with the reference
    		assertEquals(decompiled_content, reference_content);
    		
		} catch(Exception ex) {
			System.out.println("ERROR: testing file " + file.getCanonicalPath());
			ex.printStackTrace();
		}
		
	}

}
