import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <T, U> U foo(@DelegatesTo.Target('a') T a, @DelegatesTo(target = 'a', strategy = 1) @ClosureParams(FirstParam) Closure<U> cl) {
  a.with cl
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