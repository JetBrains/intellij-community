def <V0 extends X0, X0> void foo(C<? extends V0> a, C<V0> b, C<X0> c) {
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