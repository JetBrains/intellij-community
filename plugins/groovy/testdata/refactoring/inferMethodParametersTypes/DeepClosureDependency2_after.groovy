import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <U1 extends A> Object foo(U1 a, @ClosureParams(value = FromString, options = ["? super java.util.List<U1>,U1"]) Closure<Void> c) {
  c([a], a)
  a.x()
}

class A{def x(){}}

def bar(A x) {
  foo(x) {a, b ->}
}
