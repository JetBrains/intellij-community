// "Add non-null asserted (arg[42, 13]!!) call" "false"
// ACTION: Convert to block body
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Introduce local variable
// ACTION: Replace overloaded operator with function call
// WITH_STDLIB
operator fun Int.get(row: Int, column: Int) = if (row == column) this else null
fun foo(arg: Int) = arg[42, 13]<caret>.hashCode()
