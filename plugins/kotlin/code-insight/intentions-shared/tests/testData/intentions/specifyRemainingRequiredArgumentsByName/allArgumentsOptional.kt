// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(a: Int = 5, b: Int = 5, c: Int = 5) {}

fun test() {
    foo(<caret>)
}