def <V0 extends java.util.List<?>> Object foo(List<V0> a, List<? extends V0> b) {
  a.add(b.get(0))
}

class A{}
class B{}

void  m (A a, B b) {
  foo([[a]], [[a]])
  foo([[b]], [[b]])
}