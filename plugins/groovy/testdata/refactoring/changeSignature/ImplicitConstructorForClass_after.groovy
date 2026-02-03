class Foo {
  def Fo<caret>o(int p) {}
}

class Bar extends Foo {
    def Bar() {
        super(5)
    }

    def get() {return "a"}
}