class X {
  def foo = 2

  def a() {
    def f<caret>o = 3;

    fo = 5
    print fo

    print foo
  }
}