class Test {
  void nestedParenthesisTest(int a, int b, int c) {
    int d = c * a + c * b;
    int e = c * (a) + c * b;
    int f = a - b + c;
    int g = a - (b) + c;
  }
}