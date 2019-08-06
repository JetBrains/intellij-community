import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@DelegatesTo.Target('a') Object a, @DelegatesTo(target = 'a', strategy = Closure.TO_SELF) @ClosureParams(value = SimpleType, options = ['?']) Closure<Exception> cl) {
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