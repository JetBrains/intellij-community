package javadefault.renaming;

/**
 * for AssertionsAndRenaming.java
 *
 */
public class A1 {
  public static int i = 1;
  
  public int foo() {
    assert i < 4;
    System.out.println("A1.foo");
    return 1;
  }
  
  public static class C {
    public int bar() {
      assert i < 3;
      System.out.println("A1.C.bar");
      return 2;
    }
  }
  
  /** cannot have real main, renaming of classes would mean this main is no longer found. **/
  public static void mainXXX(String[] args) {
    assert i > 0;
    A1 a = new A1();
    a.foo();
    C c = new C();
    c.bar();
    System.out.println("A1 Done.");
  }
}
