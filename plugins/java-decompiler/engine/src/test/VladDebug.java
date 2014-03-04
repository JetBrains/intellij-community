package test;

import java.io.File;

import de.fernflower.main.decompiler.ConsoleDecompiler;


public class VladDebug {

	public static void main(String[] args) {

		try {
			
			ConsoleDecompiler decompiler = new ConsoleDecompiler();

			decompiler.addSpace(new File("C:\\Temp\\fernflower\\vladdebug\\"), true);
			
			decompiler.decompileContext(new File("D:\\Nonbku\\workspace_3.4\\vladdebug\\src\\"));
			
			
//			Fernflower fl = new Fernflower();
//			
//			DecompilerContext.getCurrentContext().setLogger(new PrintStreamLogger(IFernflowerLogger.WARNING, System.out));
//
//			GlobalOptions.setProperty(GlobalOptions.DECOMPILE_INNER, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.DECOMPILE_CLASS_1_4, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.DECOMPILE_ASSERTIONS, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.REMOVE_BRIDGE, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.REMOVE_SYNTHETIC, "0"); 
//			GlobalOptions.setProperty(GlobalOptions.HIDE_EMPTY_SUPER, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.HIDE_DEFAULT_CONSTRUCTOR, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.DECOMPILE_GENERIC_SIGNATURES, "0"); 
//			GlobalOptions.setProperty(GlobalOptions.OUTPUT_COPYRIGHT_COMMENT, "0"); 
//			GlobalOptions.setProperty(GlobalOptions.NO_EXCEPTIONS_RETURN, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.DECOMPILE_ENUM, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.FINALLY_DEINLINE, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.REMOVE_GETCLASS_NEW, "1"); 
//
//			GlobalOptions.setProperty(GlobalOptions.BOOLEAN_TRUE_ONE, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.SYNTHETIC_NOT_SET, "1"); 
//			GlobalOptions.setProperty(GlobalOptions.UNDEFINED_PARAM_TYPE_OBJECT, "1"); 
//
//			
//			if(args != null && args.length > 1) {
//				
//				for(int i=0;i<args.length-1;i++) {
//					fl.getStructcontext().addSpace(new File(args[i]), true);
//				}
//
//				fl.decompileContext(new File(args[args.length-1]));
//				
//				return;
//			}
//			
//			StructContext context = fl.getStructcontext();
//
//			context.addSpace(new File("C:\\Temp\\fernflower\\vladdebug\\"), true);
//		
//			fl.decompileContext(context, new File("D:\\Nonbku\\workspace_3.4\\vladdebug\\src\\"));
			
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		
	}
	
}
