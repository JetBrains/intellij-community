def foo() {}

Closure c = {
    this.<ref>foo()
}
c.call()