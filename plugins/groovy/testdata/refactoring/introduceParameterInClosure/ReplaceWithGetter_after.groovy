class X {
  def foo

  def bar = { final def anObject ->
      print anObject
  }
}

final X x = new X()
print x.bar(x.foo)
