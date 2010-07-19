class X {
  static def foo = 2

  static def a() {
    def f<caret>o = 3;

    fo = 5
    print fo

    print foo
  }
}