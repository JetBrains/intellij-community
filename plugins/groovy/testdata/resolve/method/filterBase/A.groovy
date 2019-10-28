class A {
  def foo() {}
}

class B extends A {
  def foo() {}
}

new B().<caret>foo()