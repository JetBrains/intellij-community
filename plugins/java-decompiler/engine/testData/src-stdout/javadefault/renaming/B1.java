package javadefault.renaming;

/**
 * renaming did not work for access to .class (used from ClassFieldRenaming.java)
 */
public class B1 {

    public void f() {
        Class x = B1.class;
        Class y = I.class;
        Class z = I.J.class;
        printWhichClass(x);
        printWhichClass(y);
        printWhichClass(z);
    }
    
    public static class I {
        public void f() {
            Class x = B1.class;
            Class y = I.class;
            Class z = J.class;
            
            Class xa = I[].class;
            
            printWhichClass(x);
            printWhichClass(y);
            printWhichClass(z);
            printWhichClass(xa);
        }
        
        public static class J {
            public void f() {
                Class x = B1.class;
                Class y = I.class;
                Class z = J.class;
                
                printWhichClass(x);
                printWhichClass(y);
                printWhichClass(z);
            }
        }
    }
    
    public static void printWhichClass(Object x) {
      System.out.println("B1: printWhichClass:");
      System.out.println(new B1().getClass().equals(x));
      System.out.println(new I().getClass().equals(x));
      System.out.println(new I.J().getClass().equals(x));
    }
    
    /** cannot have real main, renaming of classes would mean this main is no longer found. **/
    public static void mainXXX(String[] args) {
      new B1().f();
      new I().f();
      new I.J().f();
    }
}

