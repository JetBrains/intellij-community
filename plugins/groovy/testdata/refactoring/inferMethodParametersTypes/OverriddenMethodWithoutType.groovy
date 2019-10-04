class A{
  def foo(a) {}
}

class B extends A {

  @Override
  def fo<caret>o(a) {

  }
}

def m(B b) {
  b.foo(1)
}