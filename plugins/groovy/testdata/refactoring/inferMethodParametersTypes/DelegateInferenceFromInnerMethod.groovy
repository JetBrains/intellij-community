def foo(a, cl) {
  m(a, cl)
}

class A {
  void bar() {}
}

class B {
  void baz() {}
}

foo(new A()) {
  bar()
}

foo(new B()) {
  baz()
}


def m(@DelegatesTo.Target() Object a, @DelegatesTo(strategy = Closure.TO_SELF) Closure<Exception> closure) {

}