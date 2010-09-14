class X {
  def foo(def it = null) {print it}

  def bar() {
    foo(2)

    this.foo(2)

    foo(2)
    foo()

    print this.&foo
  }
}

final X x = new X()
x.foo(2)
print x.&foo
x.foo(2)
x.foo()

