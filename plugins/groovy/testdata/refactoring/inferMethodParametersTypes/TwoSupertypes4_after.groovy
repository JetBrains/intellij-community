def <T0> void foo(List<T0> a, List<T0> b, List<T0> c, List<? extends T0> d) {
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

