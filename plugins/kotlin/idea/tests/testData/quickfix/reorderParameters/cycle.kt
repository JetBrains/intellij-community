// "Reorder parameters" "false"
// ACTION: Add '@JvmOverloads' annotation to function 'foo'
// ACTION: Convert to block body
// ACTION: Enable 'Types' inlay hints
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Flip ',' (may change semantics)
// ACTION: Put parameters on one line
// ACTION: Specify return type explicitly
// ERROR: Parameter 'y' is uninitialized here
fun foo(
    x: String = y<caret>,
    y: String = x
) = Unit
