def <U0> void foo(C<U0> a, C<U0> b, C<U0> e, C<? extends U0> f) {
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