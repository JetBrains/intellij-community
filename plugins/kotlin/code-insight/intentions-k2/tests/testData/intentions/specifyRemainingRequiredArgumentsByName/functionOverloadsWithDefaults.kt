// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(a: Int, b: String) {}
fun foo(a: Int, b: String, c: Int) {}
fun foo(a: Int, b: String = "", c: Int = 5, d: Int = 5) {}

fun test() {
    foo<caret>()
}