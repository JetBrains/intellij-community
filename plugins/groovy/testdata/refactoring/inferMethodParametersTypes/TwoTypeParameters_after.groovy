def <T0 extends java.io.Serializable, U0 extends java.io.Serializable> Object foo(C<T0, U0> a, T0 b, U0 c) {
  a.doTU(b, c)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

foo(new C<Integer, String>(), 1, 's')
foo(new C<String, Integer>(), 's', 1)