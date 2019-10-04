Closure<?> foo(@DelegatesTo(A) Closure<?> cl) {
  A a = new A()
  cl.rehydrate(a, this, this)
}

class A {
  void bar() {}
}

foo {
  bar()
}