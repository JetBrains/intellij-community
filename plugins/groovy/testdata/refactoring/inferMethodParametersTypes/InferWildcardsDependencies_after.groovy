def <T0> void foo(List<T0> a, T0 x) {
  a.add(x)
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], a)

  foo([b], b)
}
