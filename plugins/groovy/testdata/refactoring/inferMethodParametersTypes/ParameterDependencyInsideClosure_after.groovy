import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T, U extends T> Boolean foo(@ClosureParams(value = FromString, options = ["? super java.util.ArrayList<T>,U"]) Closure<Boolean> cl) {
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