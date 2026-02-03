class X {
  def fo<caret>o = {print it}

  def bar() {
    foo(2)

    this.foo(2)

    foo.call(2)
    foo.call()

    print this.&foo
  }
}

final X x = new X()
x.foo(2)
print x.foo
x.foo.call(2)
x.foo.call()

