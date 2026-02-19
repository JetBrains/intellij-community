// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: String) {}
fun foo(a: Int) {}

fun test() {
    foo(<caret>)
}