def foo(a, b, c) {
  a.doTU(b, c)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

foo(new C<Integer, String>(), 1, 's')
foo(new C<String, Integer>(), 's', 1)