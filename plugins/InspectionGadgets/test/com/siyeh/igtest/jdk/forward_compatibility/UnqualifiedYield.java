class Yield {
  class X {
    void test() {
      <warning descr="Unqualified call to 'yield' method might not be supported in releases after Java 13">yield</warning>("x");
      <warning descr="Unqualified call to 'yield' method might not be supported in releases after Java 13">yield</warning>(1);
    }
  }

  void test() {
    <warning descr="Unqualified call to 'yield' method might not be supported in releases after Java 13">yield</warning>("x");
    <warning descr="Unqualified call to 'yield' method might not be supported in releases after Java 13">yield</warning>(1);
  }
  
  void varYield() {
    int yield = 5;
    yield++;
    yield = 7;
  }

  void yield(int x) {}

  static void yield(String x) {
  }
}
