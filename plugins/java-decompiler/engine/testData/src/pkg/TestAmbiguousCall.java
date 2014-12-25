package pkg;

class TestAmbiguousCall {
  void m1(RuntimeException e, String s) { }
  void m1(IllegalArgumentException e, String s) { }

  void test() {
    IllegalArgumentException iae = new IllegalArgumentException();
    m1((RuntimeException)iae, "RE");
    m1(iae, "IAE");

    RuntimeException re = new IllegalArgumentException();
    m1(re, "RE");
    m1((IllegalArgumentException)re, "IAE");
  }
}
