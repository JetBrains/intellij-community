def foo(x, cl) {
  cl.delegate = x
  cl.setResolveStrategy(Closure.DELEGATE_ONLY)
}

class A {
  void bar() {}
}

class B {
  void baz() {}
}

A a = null
B b = null
foo(a) {
  bar()
}

foo(b) {
  baz()
}
