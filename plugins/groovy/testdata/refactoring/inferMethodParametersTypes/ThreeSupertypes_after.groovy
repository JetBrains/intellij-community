def <T0 extends V0, U0 extends T0, V0 extends java.io.Serializable> Object foo(C<T0, U0, V0> a, U0 b) {
  a.doU(b)
  a.doT(b)
  a.doV(b)
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