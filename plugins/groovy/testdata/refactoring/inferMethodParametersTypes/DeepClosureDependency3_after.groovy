import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0 extends A> Object foo(T0 a, @ClosureParams(value = FromString, options = ["? super java.util.List<T0>,T0"]) Closure<?> c) {
  c([a], a)
}

class A{def x(){}}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}
