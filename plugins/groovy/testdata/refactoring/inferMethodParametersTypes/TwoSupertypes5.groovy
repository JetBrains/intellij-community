def void foo(a, b, e, f) {
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