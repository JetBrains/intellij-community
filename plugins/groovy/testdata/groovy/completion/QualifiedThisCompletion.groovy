class X{
  def foo1() {}
  def foo2() {}

  class Inner{
    def foo() {
      X.this.foo<caret>
    }
  }
}