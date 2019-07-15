def <T0, V0 extends T0, X0 extends V0> void foo(C<T0> a, C<V0> b, C<X0> e, C<? extends X0> f) {
  a.tie(b.get())
  b.tie(f.get())
  e.tie(f.get())
}

class C<T> {
  void tie(T t) {}

  T get() { return null }
}

class A{}
class B{}

void m() {
  C<A> ca = null;
  C<B> cb = null;

  foo(ca, ca, ca, ca)
  foo(cb, cb, cb, cb)
}