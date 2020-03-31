class A{
  def foo(a) {}
}

class B extends A {

  @Override
  Object fo<caret>o(Object a) {

  }
}

def m(B b) {
  b.foo(1)
}