import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <X0> Object foo(@ClosureParams(value = FromString, options = ["java.util.List<X0>,X0"]) Closure<?> cl) {
  def a = new A()
  def b = new B()
  cl([a], a)
  cl([b], b)
}

class A{}
class B{}

def bar() {
  foo {a, b -> a.add(b)}
}