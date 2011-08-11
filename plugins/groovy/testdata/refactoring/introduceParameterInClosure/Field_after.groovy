class X {
  def a
  public foo = { int x, final def anObject ->
    print x
    print anObject
  }
}

final X x = new X()
x.foo(1, x.a)