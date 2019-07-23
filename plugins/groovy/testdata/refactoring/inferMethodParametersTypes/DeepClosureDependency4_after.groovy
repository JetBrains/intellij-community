import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <X0> Object foo(A a, @ClosureParams(value = FromString, options = ["java.util.List<X0>,X0"]) Closure<?> c) {
  B b = new B()
  c([b], b)
  c([a], a)
}

class A{}
class B{}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}