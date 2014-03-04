package test;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import com.vladium.utils.timing.TimerFactory;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class TestRuntime1_4_2_03 {

	public static void main(String[] args) {

		try {
			
			TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{put("log", "warn");}});
			
			decompiler.addSpace(new File("C:\\Temp\\fernflower\\runtime1_4_2_03\\"), true);
			
			decompiler.decompileContext(new File("D:\\Nonbku\\workspace_3.4\\JavaRuntime1_4_2_03\\src\\"));

			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
}
