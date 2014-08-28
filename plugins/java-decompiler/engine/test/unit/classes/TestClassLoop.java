package unit.classes;

public class TestClassLoop {

	public static void testSimpleInfinite() {

		while(true) {
     	   System.out.println();
        }

	}
	
	public static void testFinally() {

		boolean a = (Math.random() > 0);
		
		while(true) {
			try {
				if(!a) {
					return; 
				}
			} finally {
				System.out.println("1");
			}
		}
	
	}

	public static void testFinallyContinue() {

		boolean a = (Math.random() > 0);
		
		for(;;) {
    		try {
    			System.out.println("1");
    		} finally {
    			if(a) {
    				System.out.println("3");
    				continue;
    			}
    		}
    		
    		System.out.println("4");
		}
		
	}
	
}
