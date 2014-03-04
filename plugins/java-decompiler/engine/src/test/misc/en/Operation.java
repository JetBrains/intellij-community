package test.misc.en;

public enum Operation {
	
    PLUS(2) {
        double eval(double x, double y) { return x + y; }
    },
    MINUS(7) {
        double eval(double x, double y) { return x - y; }
    },
    TIMES(8) {
        double eval(double x, double y) { return x * y; }
    },
    DIVIDED_BY(0) {
        double eval(double x, double y) { return x / y; }
    };
    

    // Perform the arithmetic operation represented by this constant
    
    abstract double eval(double x, double y);
    
    Operation(int t) {
    	
//    	class LocalClass {
//    		
//    	}
//    	
//    	LocalClass e = null;
    	
    	System.out.println();
    }
    

    public static void main(String args[]) {
        double x = Double.parseDouble(args[0]);
        double y = Double.parseDouble(args[1]);

        Operation opp = Operation.DIVIDED_BY;
        
        switch(opp) {
        	case MINUS:
        		System.out.println();
        	case PLUS:
        }

        for (Operation op : Operation.values()) {
            System.out.println(x + " " + op + " " + y + " = " + op.eval(x, y));
        }
    }
}