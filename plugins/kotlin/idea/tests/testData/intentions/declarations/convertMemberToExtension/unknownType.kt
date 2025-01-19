// ERROR: A 'return' expression required in a function with a block body ('{...}')
// ERROR: Unresolved reference: Foo
// ERROR: Unresolved reference: bar
// AFTER-WARNING: Parameter 'p' is never used
// K2-ERROR: Missing return statement.
// K2-ERROR: Unresolved reference 'Foo'.
// K2-ERROR: Unresolved reference 'bar'.
// K2-AFTER-ERROR: Missing return statement.
// K2-AFTER-ERROR: Unresolved reference 'Foo'.
// K2-AFTER-ERROR: Unresolved reference 'bar'.

class Owner {
    fun <caret>f(p: Foo): bar.Baz {
    }
}
