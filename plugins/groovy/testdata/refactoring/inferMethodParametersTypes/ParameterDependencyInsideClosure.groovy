def foo(cl) {
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