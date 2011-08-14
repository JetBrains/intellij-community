class X {
  def foo

  def bar = {
    print <selection>foo</selection>
  }
}

print new X().bar()
