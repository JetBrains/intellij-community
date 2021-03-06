def <T extends ArrayList<?>> boolean foo(ArrayList<T> a, ArrayList<? extends T> b) {
  a.add(b.get(0))
}

class A{}
class B{}

void  m (A a, B b) {
  foo([[a]], [[a]])
  foo([[b]], [[b]])
}