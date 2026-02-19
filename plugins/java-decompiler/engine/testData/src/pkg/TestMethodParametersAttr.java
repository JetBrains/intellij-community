package pkg;

// compile with `javac -parameters ...`
public class TestMethodParametersAttr {
  TestMethodParametersAttr(int p01) { System.out.print(p01); }
  void m1(int p02) { System.out.print(p02); }
  static void m2(int p03) { System.out.print(p03); }

  class C1 {
    C1(int p11) { System.out.print(p11); }
    void m(int p12) { System.out.print(p12); }
  }

  static class C2 {
    C2(int p21) { System.out.print(p21); }
    void m1(int p22) { System.out.print(p22); }
    static void m2(int p23) { System.out.print(p23); }
  }

  void local() {
    class Local {
      Local(int p31) { System.out.print(p31); }
      void m(int p32) { System.out.print(p32); }
    }
  }

  interface I1 {
      void m1(int p41);
      void m2(final int p42);
  }

  abstract class C3 {
    abstract void m1(int p51);
    abstract void m2(final int p52);
  }

  static abstract class C4 {
    abstract void m1(int p61);
    abstract void m2(final int p62);
  }

  enum E1 {
    ;
    E1(int p71) { System.out.print(p71); }
  }
}
