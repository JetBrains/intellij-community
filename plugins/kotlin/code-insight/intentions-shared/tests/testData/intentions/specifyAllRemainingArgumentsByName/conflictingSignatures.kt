// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: Int, b: Int, c: Int, d: Int) {}

fun test() {
    foo(a = ""<caret>)
}