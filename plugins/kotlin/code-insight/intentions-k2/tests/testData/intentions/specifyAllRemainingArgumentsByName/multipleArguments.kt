// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: Int, c: Int, d: Int): Int = a + b + c + d

fun test() {
    foo(<caret>)
}