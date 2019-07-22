def foo(a, c) {
  B b = new B()
  c([b], b)
  c([a], a)
}

class A{}
class B{}

def bar(A x) {
  foo(x) {a, b -> a.add(b)}
}