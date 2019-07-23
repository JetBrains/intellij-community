import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

Object foo(A a, @ClosureParams(value = FromString, options = ["java.util.List<? extends A>,A"]) Closure<?> c) {
  c([a], a)
}

class A{}

def bar(A x) {
  foo(x) {a, b ->}
}
