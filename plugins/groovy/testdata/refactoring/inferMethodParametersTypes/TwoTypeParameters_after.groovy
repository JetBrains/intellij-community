def <T, U> Object foo(C<T, U> a, T b, U c) {
  a.doTU(b, c)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

class A{}
class B{}
def m(A a, B b) {
  foo(new C<A, B>(), a, b)
  foo(new C<B, A>(), b, a)
}
