import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@DelegatesTo.Target('x') Object x, @DelegatesTo(target = 'x', strategy = Closure.DELEGATE_ONLY) @ClosureParams(value = SimpleType, options = ['?']) Closure<?> cl) {
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
