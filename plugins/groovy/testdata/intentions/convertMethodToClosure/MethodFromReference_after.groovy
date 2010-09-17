class X{
  def a;

  def foo = {def x, def y ->
    print x + a;
    print y;
  }
}

new X().foo(2, 3)