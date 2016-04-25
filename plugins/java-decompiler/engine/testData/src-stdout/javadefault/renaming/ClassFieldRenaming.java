package javadefault.renaming;

/**
 * renaming did not work for access to .class
 */
public class ClassFieldRenaming {

    public void f() {
        Class x = ClassFieldRenaming.class;
        Class y = I.class;
        Class z = I.J.class;
        printWhichClass(x);
        printWhichClass(y);
        printWhichClass(z);
    }
    
    public static class I {
        public void f() {
            Class x = ClassFieldRenaming.class;
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
                Class x = ClassFieldRenaming.class;
                Class y = I.class;
                Class z = J.class;
                
                printWhichClass(x);
                printWhichClass(y);
                printWhichClass(z);
            }
        }
    }
    
    public static void printWhichClass(Object x) {
      System.out.println("printWhichClass:");
      System.out.println(new ClassFieldRenaming().getClass().equals(x));
      System.out.println(new I().getClass().equals(x));
      System.out.println(new I.J().getClass().equals(x));
    }
    
    public static void main(String[] args) {
      new ClassFieldRenaming().f();
      new I().f();
      new I.J().f();
      A.mainXXX(args);
    }
}

