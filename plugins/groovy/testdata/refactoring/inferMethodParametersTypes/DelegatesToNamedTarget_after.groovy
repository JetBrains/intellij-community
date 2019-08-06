import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@DelegatesTo(A) @ClosureParams(value = SimpleType, options = ['?']) Closure<Exception> cl) {
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