// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(a: Int, vararg b: Int) {}

fun test() {
    foo(<caret>)
}