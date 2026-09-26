def <T, U extends T, V extends U> Object foo(C<T, U, V> a, V b) {
  a.doTU(b, b)
  a.doU(b)
  a.doT(b)
  a.doV(b)
  a.doTUV(b, b, b)
}

class C<T, U, V> {
  void doT(T t) {}
  void doU(U u) {}
  void doV(V v) {}
  void doTU(T t, U u) {}
  void doTUV(T t, U u, V v) {}
}
class A{}
class B{}
def m(A a, B b) {
  foo(new C<A, A, A>(), a)
  foo(new C<B, B, B>(), b)
}