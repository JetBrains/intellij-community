def <U0 extends List<?>> Object foo(List<U0> a, List<? extends U0> b) {
  a.add(b.get(0))
}

class A{}
class B{}

void  m (A a, B b) {
  foo([[a]], [[a]])
  foo([[b]], [[b]])
}