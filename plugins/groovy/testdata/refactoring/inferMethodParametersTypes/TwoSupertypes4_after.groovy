def <T0, V0 extends X0, X0 extends T0> void foo(List<T0> a, List<V0> b, List<X0> c, List<? extends V0> d) {
  a.add(b.get(0))
  c.add(d.get(0))
  b.add(d.get(0))
}

class A{}
class B{}

def m(A a, B b) {
  foo([a], [a], [a], [a])
  foo([b], [b], [b], [b])
}

