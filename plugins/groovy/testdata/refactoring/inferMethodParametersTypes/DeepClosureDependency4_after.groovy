import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T> Object foo(T a, @ClosureParams(value = FromString, options = ["? super java.util.ArrayList<T>,T"]) Closure<?> c) {
  c([a], a)
}

class A{def x(){}}
class B{def x(){}}

def bar(A x, B y) {
  foo(x) {a, b -> a.add(b)}
  foo(y) {a, b -> a.add(b)}
}
