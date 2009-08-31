class X extends Y{
  def foo3() {}
  def foo4() {}

  class Inner{
    def foo() {
      X.super.foo<caret>
    }
  }
}

class Y {
  def foo1(){}
  def foo2(){}
}