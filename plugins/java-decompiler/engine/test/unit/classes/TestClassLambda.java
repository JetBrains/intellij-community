package unit.classes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.IntBinaryOperator;
import java.util.function.Supplier;

public class TestClassLambda {

	public int field = 0;
	
	public void testLambda() {
		
		List<Integer> list = Arrays.asList(1, 2, 3, 4, 5, 6, 7);
		int b = (int)Math.random();
		
		list.forEach(n -> {int a = 2 * n; System.out.println(a + b + field);});	
	}

	public void testLambda1() {
		
		int a = (int)Math.random();
		
		Runnable r = () -> { System.out.println("hello" + a); };

		Runnable r1 = () -> { System.out.println("hello1" + a); };
	}

	public void testLambda2() { 
		reduce((left, right) -> Math.max(left, right));
	}

	public void testLambda3() { // IDEA-127301
		reduce(Math::max);
	}

	public void testLambda4() { 
		reduce(TestClassLambda::localMax);
	}
	
	public void testLambda5() { 
		String x = "abcd";
		function(x::toString);
	}
	
	public void testLambda6() {
        List<String> list = new ArrayList<String>();
        int bottom = list.size() * 2; 
        int top = list.size() * 5;
        list.removeIf( s -> (bottom >= s.length() && s.length() <= top) );
    }
	
	public static OptionalInt reduce(IntBinaryOperator op) {
	    return null;
	}
	
	public static String function(Supplier<String> supplier) {
	    return supplier.get();
	}

	public static int localMax(int first, int second) {
	    return 0;
	}

}
