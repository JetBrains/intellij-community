// "Reorder parameters" "false"
// ACTION: Add '@JvmOverloads' annotation to function 'foo'
// ACTION: Convert to block body
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Flip ',' (may change semantics)
// ACTION: Put parameters on one line
// ACTION: Specify return type explicitly
// ERROR: Parameter 'y' is uninitialized here
fun foo(
    x: String = y<caret>,
    y: String = x
) = Unit
