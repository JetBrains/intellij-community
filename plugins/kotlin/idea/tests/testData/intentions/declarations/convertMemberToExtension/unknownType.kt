// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: Foo
// ERROR: Unresolved reference: bar
// AFTER-WARNING: Parameter 'p' is never used

class Owner {
    fun <caret>f(p: Foo): bar.Baz {
    }
}
