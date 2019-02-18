class Test {

  void test(boolean b, boolean c, boolean d) {
    boolean a = !(!b && !c || !b && d);
  }

}