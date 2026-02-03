// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(vararg a: Int) {}

fun test() {
    foo(<caret>)
}