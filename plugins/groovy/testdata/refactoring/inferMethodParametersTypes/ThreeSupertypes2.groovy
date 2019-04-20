def foo(a, b) {
  a.doTU(b, b)
  a.doU(b)
  a.doT(b)
  a.doV(b)
  a.doTUV(b, b, b)
}

class C<T, U, V> {
  void doT(T t) {}
  void doU(U u) {}
  void doV(V v) {}
  void doTU(T t, U u) {}
  void doTUV(T t, U u, V v) {}
}
foo(new C<String, String, String>(), 'q')
foo(new C<Integer, Integer, Integer>(), 2)