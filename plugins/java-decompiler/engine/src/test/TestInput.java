package test;

import java.io.File;
import java.util.Date;

import com.vladium.utils.timing.TimerFactory;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class TestInput {

	public static void main(String[] args) {

		try {
			TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
		File[] dirs = new File("C:\\revjava\\remote\\data\\input\\").listFiles();
		
		for(File dir : dirs) {
			if(dir.isDirectory()) {
				Date start = new Date();
				System.out.println("==========================================================================");
				System.out.println("Processing " + dir.getAbsolutePath());
				decompileDirectory(dir);
				System.out.println("Proceeded " + dir.getAbsolutePath());
				System.out.println("Time elapsed " + (new Date().getTime() - start.getTime())/1000);
				System.out.println("==========================================================================");
			}
		}
		
	}
	
	private static void decompileDirectory(File dir) {
		
		try {
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler();
			
			decompiler.addSpace(dir, true);
			
			decompiler.decompileContext(new File("C:\\Temp\\fernflower\\dec\\output\\", dir.getName()));
			
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}

}
