Object foo(@DelegatesTo(value = A, strategy = Closure.DELEGATE_ONLY) Closure<?> cl) {
  A a = new A()
  cl.delegate = a
  cl.setResolveStrategy(Closure.DELEGATE_ONLY)
}

class A {
  void bar() {}
}

foo {
  bar()
}