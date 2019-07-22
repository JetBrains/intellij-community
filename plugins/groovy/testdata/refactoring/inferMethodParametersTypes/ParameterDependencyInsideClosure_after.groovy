import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <U0> Object foo(@ClosureParams(value = FromString, options = ["java.util.List<U0>,U0"]) Closure<?> cl) {
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