def <T0, U0 extends T0> Object foo(C<T0, U0> a, U0 b) {
  a.doTU(b, b)
  a.doU(b)
  a.doT(b)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

class A{}
class B{}
def m(A a, B b) {
  foo(new C<A, A>(), a)
  foo(new C<B, B>(), b)
}

