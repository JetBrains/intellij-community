import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <Y0> Object foo(A a, @ClosureParams(value = FromString, options = ["? super java.util.List<Y0>,Y0"]) Closure<?> c) {
  B b = new B()
  c([b], b)
  c([a], a)
}

class A{}
class B{}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}