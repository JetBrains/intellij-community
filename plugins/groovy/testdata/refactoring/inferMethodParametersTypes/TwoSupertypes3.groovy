def foo(a, b) {
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

