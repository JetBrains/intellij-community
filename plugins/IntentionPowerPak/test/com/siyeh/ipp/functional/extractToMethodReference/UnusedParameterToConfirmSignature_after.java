interface I {
  void m(int i);
}
class B {
    private static void m(int i1) {
        System.out.println("");
    }

    {
    I i = B::m;
  }
}