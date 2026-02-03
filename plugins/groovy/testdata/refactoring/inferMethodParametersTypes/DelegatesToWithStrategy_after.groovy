Object foo(@DelegatesTo(value = A, strategy = Closure.TO_SELF) Closure<Exception> cl) {
  A a = null
  m(a, cl)
}

class A {
  void bar() {}
}

foo {
  bar()
}

def m(@DelegatesTo.Target() Object a, @DelegatesTo(strategy = Closure.TO_SELF) Closure<Exception> closure) {

}