package test;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import de.fernflower.main.decompiler.ConsoleDecompiler;

public class JetTest {

	public static void main(String[] args) {

		try {
			
			String current_path = new File(".").getCanonicalPath();
			
			//TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{put("log", "warn");put("ren", "1");}});
			
		
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\BradTest.class"), true);
			decompiler.addSpace(new File(current_path + "/bin/test/input/TestEclipse7.class"), true);
			//decompiler.addSpace(new File(current_path + "/src/test/input/TestJavac7.class"), true);
			//decompiler.addSpace(new File(current_path + "/src/test/input/TestJavac8.class"), true);

			//decompiler.decompileContext(new File("D:\\Oleg\\workspace\\output\\"));
			decompiler.decompileContext(new File(current_path + "/src/test/output/"));
			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
	
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
}
