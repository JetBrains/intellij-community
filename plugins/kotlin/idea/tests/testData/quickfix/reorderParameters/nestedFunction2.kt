// "Reorder parameters" "false"
// ACTION: Add '@JvmOverloads' annotation to function 'bar'
// ACTION: Convert to block body
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Function return types' for 'Types' inlay hints
// ACTION: Flip ',' (may change semantics)
// ACTION: Put parameters on one line
// ACTION: Specify return type explicitly
fun foo(b: Int) {
    fun bar(
        a: Int = b<caret>,
        c: Int = 2
    ) = Unit
}
