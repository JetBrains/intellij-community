package test.input;

import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class TestJavac8 {

//	public static void main(String args[]) {
//		new TestJavac8().testLambda();
//	}
//	
//	public void testTryResources() throws IOException {
//		
//		try (FileReader reader = new FileReader("file"); FileReader reader1 = new FileReader("file")) {
//			System.out.println();
//		}
//		
//	}
//
//	public void testMultiCatch() {
//
//		try {
//			Method method = getClass().getDeclaredMethod("foo");
//		} catch (NoSuchMethodException | SecurityException e) {
//			e.printStackTrace();
//		}
//	}
//	
//
//	private void testSwitchString() {
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

	public void testLambda() {
		
		List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
		list.forEach(n -> {int a = 2 * n; System.out.println(a);});	
	}
	
}
