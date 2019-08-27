import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T extends A> Object foo(T a, @ClosureParams(value = FromString, options = ["? super java.util.List<T>,T"]) Closure<Boolean> c) {
  c([a], a)
}

class A{def x(){}}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}
