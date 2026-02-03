class X{
  def foo(int x){}
  def foo(int x, int y){}

  def abc() {
    foo<warning descr="'foo' in 'X' cannot be applied to '(java.lang.String)'">("s")</warning>
  }

}