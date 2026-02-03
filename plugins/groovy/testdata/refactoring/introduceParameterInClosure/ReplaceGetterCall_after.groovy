class X {
  def foo

  def bar = { final anObject ->
      print anObject
  }
}

final X x = new X()
print x.getBar()(x.foo)
