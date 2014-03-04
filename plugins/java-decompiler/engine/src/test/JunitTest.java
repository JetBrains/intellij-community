package test;

import java.io.File;
import java.util.Date;
import java.util.HashMap;

import de.fernflower.main.decompiler.ConsoleDecompiler;
import de.fernflower.main.extern.IFernflowerPreferences;

public class JunitTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		try {
			
			String current_path = new File(".").getCanonicalPath();
			
			//TimerFactory.initialize(new File(".").getCanonicalPath()+"/lib/timer/hrtlib.dll");
			
			Date start = new Date();
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler(new HashMap<String, Object>(){{
				put("log", "warn");
				put("ren", "1");
		        put(IFernflowerPreferences.HIDE_DEFAULT_CONSTRUCTOR, "0");
		        //put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
		        put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "0");
		        put(IFernflowerPreferences.IDEA_NOT_NULL_ANNOTATION, "1");
		     }});
			
		
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\idea-junit.jar"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\svn4idea.jar"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\github.jar"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\copyright.jar"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\rt_1_4_2.jar"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\swingset3_acceptance\\swingset3_original\\lib\\TimingFramework.jar"), true);
			
//			decompiler.decompileContext(new File("D:\\Oleg\\workspace\\output1\\"));

//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine$1$1.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine$1.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine$2.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine$StateInfo.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine$TestProgressListener.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitStatusLine.class"), true);

//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitConfiguration$1.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitConfiguration$2.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitConfiguration$Data.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\JUnitConfiguration.class"), true);
			
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\TestPackage$ResetConfigurationModuleAdapter.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\TestPackage$SearchForTestsTask.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\TestPackage.class"), true);
			
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\TestAnnotations$TestInner.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\TestAnnotationsEclipse$TestInner.class"), true);
		
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\LoadRecentBranchRevisions.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\LoadRecentBranchRevisions$1.class"), true);
			
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\Fernflower\\bin\\test\\input\\TestLoop.class"), true);
			
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\GithubSelectForkPanel$1.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\GithubSelectForkPanel.class"), true);
			
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$1.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$Caches.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$DebugTraceInfoStack.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$PutField.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$PutFieldImpl.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$HandleTable.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$ReplaceTable.class"), true);
//			decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream$BlockDataOutputStream.class"), true);
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\ObjectOutputStream.class"), true); // writeArray SSAU endless loop
			
			//decompiler.addSpace(new File("D:\\Oleg\\workspace\\input\\SvnFormatSelector.class"), true);

			decompiler.addSpace(new File("D:\\Oleg\\workspace\\Fernflower\\src\\test\\input\\TestJavac8.class"), true);
			
			decompiler.decompileContext(new File("D:\\Oleg\\workspace\\output1\\"));
			
			System.out.println("\n\nTime elapsed " + (new Date().getTime() - start.getTime())/1000);
	
		} catch(Exception ex) {
			ex.printStackTrace();
		}
	
	}

}
