// "Remove single lambda parameter declaration" "false"
// ACTION: Convert to also
// ACTION: Convert to anonymous function
// ACTION: Convert to apply
// ACTION: Convert to multi-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Enable option 'Local variable types' for 'Types' inlay hints
// ACTION: Remove explicit lambda parameter types (may break code)
// ACTION: Rename to _
fun test() {
    val f = { <caret>i: Int -> foo() }
    bar(f)
}

fun foo() {}
fun bar(f: (Int) -> Unit) {}
