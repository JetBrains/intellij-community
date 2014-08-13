package unit.classes;

public class TestClassTypes {

    public void testBoolean() {

    	byte var7 = 0;
        long time = System.currentTimeMillis();
        
        if(time % 2 > 0) {
            var7 = 1;
        } else if(time % 3 > 0) {
            var7 = 2;
        }

        if(var7 == 1) {
        	System.out.println();
        }
    }
	
}
