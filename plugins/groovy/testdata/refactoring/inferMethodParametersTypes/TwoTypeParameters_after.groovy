def <U0, V0> Object foo(C<U0, V0> a, U0 b, V0 c) {
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
