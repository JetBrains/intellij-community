def <T0, U0 extends T0> void foo(List<T0> a, List<U0> b) {
  a.add(b.get(0))
}


class A{}
class B{}

def m(A a, B b) {
  foo([a], [a])
  foo([b], [b])
}
