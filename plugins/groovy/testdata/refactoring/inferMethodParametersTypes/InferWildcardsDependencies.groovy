def void foo(a, x) {
  a.add(x)
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], a)

  foo([b], b)
}
