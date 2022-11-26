// "Remove single lambda parameter declaration" "false"
// ACTION: Convert to anonymous function
// ACTION: Convert to multi-line lambda
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Remove explicit lambda parameter types (may break code)
// ACTION: Rename to _
fun ((Boolean) -> Unit).foo() {}

fun test() {
    { <caret>b: Boolean -> }.foo()
}
