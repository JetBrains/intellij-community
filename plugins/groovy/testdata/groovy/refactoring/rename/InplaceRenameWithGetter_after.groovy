class X {
  def getFoo(){}

  def a() {
    def foo = 3;

    foo = 5
    print foo

    print this.foo
  }
}