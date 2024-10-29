// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun foo(n: Int): String {
    return <caret>$$"Bar" + n + "!"
}