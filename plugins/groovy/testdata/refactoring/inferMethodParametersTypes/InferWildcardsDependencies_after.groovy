def <U0> void foo(List<U0> a, U0 x) {
  a.add(x)
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], a)

  foo([b], b)
}
