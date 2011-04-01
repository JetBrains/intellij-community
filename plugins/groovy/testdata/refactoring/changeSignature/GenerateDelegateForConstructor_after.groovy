class Foo {
  def F<caret>oo(String s, int a) {

  }

    def Foo(String s) {
        this(s, 5);
    }

    def Foo() {
    this("a")

    print new Foo("b");
  }
}