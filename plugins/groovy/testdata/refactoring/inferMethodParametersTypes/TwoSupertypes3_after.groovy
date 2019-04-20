def <U0 extends java.io.Serializable> Object foo(C<U0, U0> a, U0 b) {
  a.doTU(b, b)
  a.doU(b)
  a.doT(b)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}
foo(new C<String, String>(), 'q')
foo(new C<Integer, Integer>(), 2)