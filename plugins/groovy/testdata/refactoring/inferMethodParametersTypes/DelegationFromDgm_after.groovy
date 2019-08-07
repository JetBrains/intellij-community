import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <X0, U0> U0 foo(@DelegatesTo.Target('a') X0 a, @DelegatesTo(target = 'a', strategy = 1) @ClosureParams(value = FromString, options = ["X0"]) Closure<U0> cl) {
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