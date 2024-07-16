// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: Int): Int = 1

fun test() {
    foo(foo(<caret>))
}