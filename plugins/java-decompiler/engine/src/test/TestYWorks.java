package test;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import com.vladium.utils.timing.TimerFactory;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class TestYWorks {

	public static void main(String[] args) {

		try {
			
			TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{put("log", "warn");put("ren", "1");}});
			
			decompiler.addSpace(new File("C:\\Temp\\fernflower\\y\\"), true);
			
			decompiler.decompileContext(new File("C:\\Temp\\fernflower\\y_ff\\"));

			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
}
