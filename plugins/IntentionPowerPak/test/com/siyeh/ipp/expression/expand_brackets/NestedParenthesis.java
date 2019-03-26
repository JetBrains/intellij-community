class Test {
  void nestedParenthesisTest(int a, int b, int c) {
    int d = c * ((a <caret>+ b));
    int e = c * ((a) <caret>+ b);
    int f = a - ((b <caret>- c));
    int g = a - (((b) <caret>- c));
  }
}