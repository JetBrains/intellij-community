package javadefault.renaming;

public class A0_AssertionsAndRenaming {
  public static int i = 1;
  
  public int foo() {
    assert i < 4;
    System.out.println("AAR.foo");
    return 1;
  }
  
  public static class X {
    public int bar() {
      assert i < 3;
      System.out.println("AAR.X.bar");
      return 2;
    }
  }
  
  public static void main(String[] args) {
    assert i > 0;
    A0_AssertionsAndRenaming a = new A0_AssertionsAndRenaming();
    a.foo();
    X x = new X();
    x.bar();
    A1.mainXXX(args);
    System.out.println("Done.");
  }
}
