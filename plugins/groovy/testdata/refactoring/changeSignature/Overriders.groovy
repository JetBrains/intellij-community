class Foo {
  def f<caret>oo(String s, double x) {}
}

class Bar extends Foo {
  def foo(String s, double x) {}
}

class Baz extends Bar {
  def foo(String s, double x) {}
}