class Foo {
  def bar = 2

  def bar() { 3 }

  public static void main(String[] args) {
    def f = new Foo()
    println f.bar
    println f.bar()

    properties<caret>
  }
}
