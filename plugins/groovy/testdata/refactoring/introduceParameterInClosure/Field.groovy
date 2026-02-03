class X {
  def a
  public foo = { int x ->
    print x
    print <selection>a</selection>
  }
}

new X().foo(1)