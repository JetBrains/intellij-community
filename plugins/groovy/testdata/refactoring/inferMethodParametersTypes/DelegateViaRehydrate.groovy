def foo(cl) {
  A a = new A()
  cl.rehydrate(a, this, this)
}

class A {
  void bar() {}
}

foo {
  bar()
}