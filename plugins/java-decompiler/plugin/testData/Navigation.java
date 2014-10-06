class Navigation {
  void m1() {
    m2(42);
  }

  void m2(int i) {
    int r = 3 * i;
    System.out.println(r);
  }
}