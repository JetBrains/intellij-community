def foo(cl) {
  A a = null
  m(a, cl)
}

class A {
  void bar() {}
}

foo {
  bar()
}

def m(@DelegatesTo.Target('a') Object a, @DelegatesTo(target = 'a') Closure<Exception> closure) {

}