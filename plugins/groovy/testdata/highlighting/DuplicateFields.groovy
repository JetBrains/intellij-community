class Bar {
  def foo = 1
  def <error descr="Field 'foo' already defined">foo</error> = 2
}