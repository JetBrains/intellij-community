import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0> Object foo(T0 a, @ClosureParams(value = FromString, options = ["? super java.util.List<T0>,T0"]) Closure<Boolean> c) {
  c([a], a)
}

class A{def x(){}}
class B{def x(){}}

def bar(A x, B y) {
  foo(x) {a, b -> a.add(b)}
  foo(y) {a, b -> a.add(b)}
}
