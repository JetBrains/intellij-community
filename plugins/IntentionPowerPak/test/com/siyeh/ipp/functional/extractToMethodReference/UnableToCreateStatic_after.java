interface I {
  void m(int i);
}
class B {
  class X {
    I i = this::m;

      private void m(int i1) {
          System.out.println("");
      }
  }
}