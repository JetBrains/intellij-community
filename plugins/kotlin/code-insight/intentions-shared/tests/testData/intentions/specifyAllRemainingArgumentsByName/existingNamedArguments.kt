// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: Int, c: Int) {}

fun test() {
    foo(c = 5<caret>)
}