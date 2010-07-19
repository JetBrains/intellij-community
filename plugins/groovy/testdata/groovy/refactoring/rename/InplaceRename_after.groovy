class X {
  def foo = 2

  def a() {
    def foo = 3;

    foo = 5
    print foo

    print this.foo
  }
}