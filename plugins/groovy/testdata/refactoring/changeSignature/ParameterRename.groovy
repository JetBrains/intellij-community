class Foo {
  def f<caret>oo(String p) {
    p = 5;
    print p;
    return p.substring(2)
  }
}

class Bar extends Foo {
  def foo(String p) {
    p = 5;
    print p;
    return p.substring(2)
  }
}