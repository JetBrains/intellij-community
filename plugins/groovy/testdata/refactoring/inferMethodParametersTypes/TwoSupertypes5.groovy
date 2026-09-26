def void foo(a, b, e, f) {
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