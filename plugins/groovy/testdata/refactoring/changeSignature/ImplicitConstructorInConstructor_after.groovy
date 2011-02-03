class Foo {
  def F<caret>oo(int p) {}
}

class Bar extends Foo {
  def Bar() {
      super(5)
  }
}
