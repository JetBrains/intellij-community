class Foo {
  boolean isSmall() {}
  Boolean isBig() {}
  def isInferred() {
    true
  }
}
def x = new Foo().small
def y = new Foo().<warning descr="Can not resolve symbol 'big'">big</warning>
def z = new Foo().<warning descr="Can not resolve symbol 'inferred'">inferred</warning>