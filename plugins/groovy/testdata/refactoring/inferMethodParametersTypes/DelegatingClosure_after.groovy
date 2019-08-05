import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@DelegatesTo(value = A, strategy = Closure.DELEGATE_ONLY) @ClosureParams(value = SimpleType, options = ['?']) Closure<?> cl) {
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