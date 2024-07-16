// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}

fun test() {
    foo(<caret>)
}