def <T1, U1 extends T1> void foo(C<T1> a, C<T1> b, C<T1> e, C<U1> f) {
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