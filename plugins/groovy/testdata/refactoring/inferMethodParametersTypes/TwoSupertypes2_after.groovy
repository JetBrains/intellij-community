def <T0 extends java.io.Serializable, U0 extends T0> Object foo(C<T0, U0> a, U0 b) {
  a.doTU(b, b)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

foo(new C<String, String>(), 'q')
foo(new C<Integer, Integer>(), 2)