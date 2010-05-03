class Foo {
  def foo(String newP) {
    newP = 5;
    print newP;
    return newP.substring(2)
  }
}

class Bar extends Foo {
  def foo(String newP) {
    newP = 5;
    print newP;
    return newP.substring(2)
  }
}