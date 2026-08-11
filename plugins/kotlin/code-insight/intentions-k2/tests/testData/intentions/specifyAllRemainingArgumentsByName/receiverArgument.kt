// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun Int.foo(a: Int, b: Int) {}

fun test() {
    1.foo(<caret>)
}