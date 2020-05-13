def foo() {}

Closure c = {
    this.<caret>foo()
}
c.call()