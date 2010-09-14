class X {
  def fo<caret>o(def it = null) {print it}

  def bar() {
    print this.&foo
  }
}

final X x = new X()
print x.&foo
x.foo(2)

