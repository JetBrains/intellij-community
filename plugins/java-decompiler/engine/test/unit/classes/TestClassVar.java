package unit.classes;


public class TestClassVar {

    private boolean field_boolean = (Math.random() > 0);
	public int field_int = 0;

	public void testFieldSSAU() {

        for(int i = 0; i < 10; i++) {
        	
        	try {
        		System.out.println();
        	} finally {
        		if (field_boolean) {
        			System.out.println();
        		}
        	}
        	
        }
	}
	
	public Long testFieldSSAU1() { // IDEA-127466
		return new Long(field_int++);
	}
	
	public void testComplexPropagation() {
		
		int a = 0;

		while (a < 10) {
			
			int b = a;

			for(; a < 10 && a == 0; a++) {}
			
			if (b != a) {
				System.out.println();
			}
		}
	}
	
}
