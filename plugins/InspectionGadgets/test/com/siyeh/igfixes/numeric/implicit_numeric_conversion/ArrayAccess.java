class Parentheses {

  void a() {
    byte[] b = {1,2,3};
    foo(b<caret>[0]);
  }

  void foo(int x) {}
}