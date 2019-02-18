class Foo {

  void f(int a, int c) {
    c = 2 - -(-<caret>a);
  }
}