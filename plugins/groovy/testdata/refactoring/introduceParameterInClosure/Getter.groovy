class X {
  public foo

  def getFoo(){foo}

  def bar = {
    print <selection>foo</selection>
  }
}

print new X().bar()
