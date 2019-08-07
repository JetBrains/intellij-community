import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Closure<?> foo(@DelegatesTo(A) @ClosureParams(value = SimpleType, options = ['?']) Closure<?> cl) {
  A a = new A()
  cl.rehydrate(a, this, this)
}

class A {
  void bar() {}
}

foo {
  bar()
}