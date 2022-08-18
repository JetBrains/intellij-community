import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <T> Object foo(@DelegatesTo.Target('a') T a, @DelegatesTo(target = 'a', strategy = 1) @ClosureParams(FirstParam) Closure<?> cl) {
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