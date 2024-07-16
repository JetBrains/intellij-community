// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(a: Int, b: () -> Unit) {}

fun test() {
    foo(b = {<caret>})
}