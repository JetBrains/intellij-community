def <U0 extends T0, T0, Y0 extends java.util.List<U0>> Object foo(List<List<U0>> a, List<Y0> b) {
  a.add(b.get(0))
}

class A{}
class B{}

void  m (A a, B b) {
  foo([[a]], [[a]])
  foo([[b]], [[b]])
}