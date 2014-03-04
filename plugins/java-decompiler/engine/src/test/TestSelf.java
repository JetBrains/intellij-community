package test;

import java.io.File;
import java.util.Date;

import com.vladium.utils.timing.TimerFactory;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class TestSelf {

	public static void main(String[] args) {

		try {

			TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler();
			
			decompiler.addSpace(new File("D:\\Nonbku\\workspace_3.4\\Fernflower\\bin\\"), true);

			decompiler.decompileContext(new File("D:\\Nonbku\\workspace_3.4\\Fernflower_dec\\src\\"));
			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
}
