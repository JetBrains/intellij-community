class Foo {
  def Foo() {
    this("a", 1)
  }

  def F<caret>oo(String s, int a) {

  }
}

class Bar extends Foo {
  def Bar() {
    super("d", 1)
  }
}