class Test {

  void foo(int a, int b, int c, int d) {
    int e = c + a * (b <caret>+ c) + a * -d;
  }

}