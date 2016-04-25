package javadefault.overloading;

/**
 * because of missing casts it created code that accessed private fields and methods...
 */
public class PrivateAccess {
    private int i = 1;
    private void bar(String x) {
      System.out.println("PrivateAccess bar called (private)");
    }
    
    public void foo(Inner inner) {
        ((PrivateAccess) inner).i = 2;
        //this decompiles to the following, which is illegal (private field access):
        //inner.i = 2;
        
        ((PrivateAccess) inner).bar("");
        //this decompiles to the following, which is illegal (private method access):
        //inner.bar("");
    }
    
    
    public void foo2(Inner2 inner) {
        ((PrivateAccess) inner).i = 2;
        ((PrivateAccess) inner).bar("");
        inner.i = 2;
        inner.bar("");
        PrivateAccess fb = new Inner2();
        ((Inner2) fb).i=2;
        ((Inner2) fb).bar("");
        fb.i=3;
        fb.bar("");
    }

    public void foo3(Inner3 inner) {
        ((PrivateAccess) inner).i = 2;
        ((PrivateAccess) inner).bar("");
        inner.i = 2;
        inner.bar("");
        PrivateAccess fb = new Inner3();
        ((Inner3) fb).i=2;
        ((Inner3) fb).bar("");
        fb.i=3;
        fb.bar("");
    }
    
    ///main
    public static void main(String[] args) {
        new PrivateAccess().foo(new Inner());
        new PrivateAccess().foo2(new Inner2());
        new PrivateAccess().foo3(new Inner3());
    }
    
    
    public static class Inner extends PrivateAccess {}
 
    ///test other cases:
    public static class Inner2 extends PrivateAccess {
        private int i = 2;
        private void bar(String x) {
          System.out.println("Inner2 bar called");
        }
    }
    
    ///test other cases:
    public static class Inner3 extends PrivateAccess {
        public int i = 2;
        public void bar(String x) {
          System.out.println("Inner3 bar called");
        }
    }
}


