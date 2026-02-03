Object foo(ArrayList<? extends X> a) {
  bar(a)
}

trait X {
  def foo() {}
}
trait Y {
  def bar() {}
}
class A implements X, Y {}
class B implements X, Y {}

def bar(List<? extends X> e) {
  e.first().foo()
}

A a = new A()
B b = new B()
foo([a])
foo([b])