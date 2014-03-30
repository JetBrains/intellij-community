interface I {}
interface X {}
interface Y {}

class A implements Y {}
class C extends A implements X {}

class Bar {
  @Delegate
  C foo
}

class Foo implements I {
  @Delegate
  Bar bar
}


X foo = new Foo()
A <warning descr="Cannot assign 'Foo' to 'A'">foo2</warning> = new Foo()
