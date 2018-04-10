class Bar {
  def <error descr="Field 'foo' already defined">foo</error> = 1
  def <error descr="Field 'foo' already defined">foo</error> = 2
}

class Baz {
  def <error descr="Field 'foo' already defined">foo</error> = 1
  def <error descr="Field 'foo' already defined">foo</error> = 2
  public def foo = 3
}