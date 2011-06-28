package b
class A {
  def foo() {
    print new B()
  }
}

class B {
  def bar() {
    print new A()
  }
}

