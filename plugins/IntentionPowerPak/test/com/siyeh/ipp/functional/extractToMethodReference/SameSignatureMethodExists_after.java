interface I {
  void m(int i);
}
class B {
    private static void m2(int i1) {
        System.out.println(i1 + "");
    }

    void m(int i) {}
  {
    I i = B::m2;
  }
}