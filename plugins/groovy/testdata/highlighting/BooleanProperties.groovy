class Foo {
  boolean isSmall() {}
  Boolean isBig() {}
  def isInferred() {
    true
  }
}
def x = new Foo().small
def y = new Foo().big
def z = new Foo().<warning descr="Cannot resolve symbol 'inferred'">inferred</warning>