def <T0 extends java.io.Serializable, U0 extends T0, V0 extends U0, W0 extends V0> void foo(C<T0> a, C<U0> b, C<V0> e, C<W0> f) {
  a.tie(b.get())
  b.tie(f.get())
  e.tie(f.get())
}

class C<T> {
  void tie(T t) {}

  T get() { return null }
}

void m() {
  C<Integer> ci = null;
  C<String> cs = null;

  foo(ci, ci, ci, ci)
  foo(cs, cs, cs, cs)
}