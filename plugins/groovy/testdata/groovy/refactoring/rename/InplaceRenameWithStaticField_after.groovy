class X {
  static def foo = 2

  static def a() {
    def foo = 3;

    foo = 5
    print foo

    print X.foo
  }
}