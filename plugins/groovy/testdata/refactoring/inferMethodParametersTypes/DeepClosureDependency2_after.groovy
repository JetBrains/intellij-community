import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <Y0 extends A> Object foo(Y0 a, @ClosureParams(value = FromString, options = ["java.util.List<Y0>,Y0"]) Closure<?> c) {
  c([a], a)
}

class A{}

def bar(A x) {
  foo(x) {a, b ->}
}
