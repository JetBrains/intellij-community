class Bar {
  @Delegate
  Foo foo
}

class Foo {
  @Delegate
  Bar bar
}


Foo foo = new Foo()