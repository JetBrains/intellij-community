def <T0, W0 extends java.util.List<T0>> Object foo(List<List<T0>> a, List<W0> b) {
  a.add(b.get(0))
}

class A{}
class B{}

void  m (A a, B b) {
  foo([[a]], [[a]])
  foo([[b]], [[b]])
}