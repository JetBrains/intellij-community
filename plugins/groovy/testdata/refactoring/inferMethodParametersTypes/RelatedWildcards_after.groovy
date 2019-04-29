def <V0, W0 extends V0> void foo(List<V0> a, List<W0> b) {
  a.add(b.get(0))
}


class A{}
class B{}

def m(A a, B b) {
  foo([a], [a])
  foo([b], [b])
}
