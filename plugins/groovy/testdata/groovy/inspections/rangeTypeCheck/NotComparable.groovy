class Foo {
  def next() {return this}
  def previous() {return this}
}

class X {
  def foo() {
    final ObjectRange range = new Foo().<caret>.new Foo()
  }
}
