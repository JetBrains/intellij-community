class Foo {
  def F<caret>oo(String s) {

  }

  def Foo() {
    this("a")

    print new Foo("b");
  }
}