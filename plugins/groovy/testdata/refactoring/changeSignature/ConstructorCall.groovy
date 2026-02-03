class Foo {
  def Foo() {
    this("a")
  }

  def F<caret>oo(String s) {

  }
}

class Bar extends Foo {
  def Bar() {
    super("d")
  }
}