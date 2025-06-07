// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: Foo
// ERROR: Unresolved reference: bar
// AFTER-WARNING: Parameter 'p' is never used
// K2_ERROR: Missing return statement.
// K2_ERROR: Unresolved reference 'Foo'.
// K2_ERROR: Unresolved reference 'bar'.
// K2_AFTER_ERROR: Missing return statement.
// K2_AFTER_ERROR: Unresolved reference 'Foo'.
// K2_AFTER_ERROR: Unresolved reference 'bar'.

class Owner {
    fun <caret>f(p: Foo): bar.Baz {
    }
}
