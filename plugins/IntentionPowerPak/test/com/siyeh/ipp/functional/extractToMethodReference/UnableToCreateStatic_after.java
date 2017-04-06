interface I {
  void m(int i);
}
class B {
  class X {
      private void m(int i1) {
          System.out.println("");
      }

      I i = this::m;
  }
}