class Foo<T> {
  static <T> Foo<T> getInstance() { new Foo<T>() }
}

Foo<String> f = Foo.inst<ref>ance
