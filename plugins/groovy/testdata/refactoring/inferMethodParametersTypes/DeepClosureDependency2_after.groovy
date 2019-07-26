import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T1 extends A> Object foo(T1 a, @ClosureParams(value = FromString, options = ["java.util.List<T1>,T1"]) Closure<?> c) {
  c([a], a)
}

class A{}

def bar(A x) {
  foo(x) {a, b ->}
}
