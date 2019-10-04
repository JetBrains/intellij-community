def <T> void foo(List<T> a, List<? extends T> b) {
  a.add(b.get(0))
}


class A{}
class B{}

def m(A a, B b) {
  foo([a], [a])
  foo([b], [b])
}
