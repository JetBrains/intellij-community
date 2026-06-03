// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int = 5, b: Int, c: Int = 5, d: Int) {}

fun test() {
    foo(<caret>)
}