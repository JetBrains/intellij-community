package test.misc;

public class MiscTest {
	
	public static double test = 1 / 0; 
	
	
	public static void main(String[] args) {
		
		System.out.println(test);
		
		String s = "a  b";
		s = s.replaceAll("  ", " &nbsp");
		System.out.println(s);
		
		try {
			throw null;
		} catch(RuntimeException ex) {
			System.out.println(ex);
		}
		
		
		int a = 3;
		
		if(a == 1) {
			System.out.println("1");
		} else if(a == 2) {
			System.out.println("2");
		} else if(a == 3) {
			System.out.println("3");
		} else if(a == 4) {
			System.out.println("4");
		} else if(a == 5) {
			System.out.println("5");
		} else if(a == 6) {
			System.out.println("6");
		} else if(a == 7) {
			System.out.println("7");
		}
		
		if(a == 0) {
			return;
		} else {
			System.out.println("0");
		}
		
		if(a==4) {
			System.out.println("assert");
			assert a==4 && a==5;
		} else {
			assert false;
		}
		
		assert a==5: Math.random();
		
		assert false: Math.random();
		
		assert true;
		
		assert true: Math.random();
		
		/*
		label: {
		if(a == 0) {
			System.out.println("0");
		} else if(a == 1) {
			System.out.println("01");
		} else {
			if(a == -1) {
				System.out.println("-1");
			} else {
				System.out.println("-2");
			}
			break label;
		}
		
		System.out.println("end");
		}
		System.out.println("end1");
		*/
	}

}
