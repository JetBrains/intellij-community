import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T extends A> Object foo(T a, @ClosureParams(value = FromString, options = ["? super java.util.List<? extends T>,T"]) Closure<Void> c) {
  c([a], a)
  a.x()
}

class A{def x(){}}

def bar(A x) {
  foo(x) {a, b ->}
}
