// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// IS_APPLICABLE: false
fun foo(a: Int) {}
fun foo(a: String, b: String, c: String) {}
fun foo(a: String, b: String, c: String, d: String) {}

fun test() {
    foo(a = 5<caret>)
}