package test.input;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;

public class TestEclipse7 {

	
//	public void testTryResources() throws IOException {
//		
//		try (FileReader reader = new FileReader("file"); FileReader reader1 = new FileReader("file")) {
//			System.out.println();
//		}
//		
//	}
//
//	public void testTryResources1() throws IOException {
//		
//		try (FileReader reader = new FileReader("file")) {
//			System.out.println("block");
//		} catch(RuntimeException ex) {
//			System.out.println(ex.toString());
//		} finally {
//			System.out.println("finally");
//		}
//		
//	}
	
	public void testMultiCatch() {

		try {
			Method method = getClass().getDeclaredMethod("foo");
		} catch (NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
	}
	

//	public void testSwitchString() {
//
//		String s = "";
//		switch(s) {
//		case "Aa": // "xyz":
//			System.out.println("!");
//			break;
//		case "BB": // "abc":
//			System.out.println("?");
//			break;
//		case "__":
//			System.out.println("_");
//			break;
//		default:
//			System.out.println("#");
//		}
//		
//	}
	
}
