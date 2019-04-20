def foo(a, b) {
  a.doTU(b, b)
}

class C<T, U> {
  void doT(T t) {}
  void doU(U u) {}
  void doTU(T t, U u) {}
}

foo(new C<String, String>(), 'q')
foo(new C<Integer, Integer>(), 2)