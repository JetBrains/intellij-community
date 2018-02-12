interface I {
  void m(int i);
}
class B {
  {
    I i = B::m;
  }

    private static void m(int i1) {
        System.out.println("");
    }
}