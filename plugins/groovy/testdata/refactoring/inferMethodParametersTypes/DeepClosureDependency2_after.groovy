import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <X0 extends A> Object foo(X0 a, @ClosureParams(value = FromString, options = ["java.util.List<X0>,X0"]) Closure<?> c) {
  c([a], a)
}

class A{}

def bar(A x) {
  foo(x) {a, b ->}
}
