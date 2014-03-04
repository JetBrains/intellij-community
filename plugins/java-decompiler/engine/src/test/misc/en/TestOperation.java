package test.misc.en;

public class TestOperation {

	public void test() {
		
        double x = 2;
        double y = 3;

        Operation opp = Operation.DIVIDED_BY;
        
        switch(opp) {
        	case MINUS:
        		System.out.println();
        	case PLUS:
        }
        
        switch(Operation.MINUS) {
    	case DIVIDED_BY:
    		System.out.println();
    	case PLUS:
    	case TIMES:
        }
        
        for (Operation op : Operation.values()) {
            System.out.println(x + " " + op + " " + y + " = " + op.eval(x, y));
        }
		
	}
	
}
