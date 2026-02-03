class X {
  public foo

  def getFoo(){foo}

  def bar = { final Object anObject ->
      print anObject
  }
}

final X x = new X()
print x.bar(x.foo)
