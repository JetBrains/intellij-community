import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T1 extends X0, X0> Boolean foo(@ClosureParams(value = FromString, options = ["? super java.util.List<X0>,T1"]) Closure<Boolean> cl) {
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