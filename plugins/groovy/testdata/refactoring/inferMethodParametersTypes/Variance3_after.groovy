def <T extends U, U> void foo(C<? extends T> a, C<T> b, C<U> c) {
  b.consume(a.produce())
  c.consume(b.produce())
}

void m(C<AA> caa, C<BB> cbb, C<A> ca, C<B> cb) {
  foo(ca, ca, caa)
  foo(cb, cb, cbb)
}

class C<T> {
  void consume(T t) {}
  T produce() {return null}
}

class AA{}
class A extends AA{}
class BB{}
class B extends BB{}