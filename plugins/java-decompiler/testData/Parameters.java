public class Parameters {
  Parameters(@Deprecated int p01) { }
  void m1(@Deprecated int p02) { }
  static void m2(@Deprecated int p03) { }

  class C1 {
    C1(@Deprecated int p11) { }
    void m(@Deprecated int p12) { }
  }

  static class C2 {
    C2(@Deprecated int p21) { }
    void m1(@Deprecated int p22) { }
    static void m2(@Deprecated int p23) { }
  }

  void local() {
    class Local {
      Local(@Deprecated int p31) { }
      void m(@Deprecated int p32) { }
    }
  }
}