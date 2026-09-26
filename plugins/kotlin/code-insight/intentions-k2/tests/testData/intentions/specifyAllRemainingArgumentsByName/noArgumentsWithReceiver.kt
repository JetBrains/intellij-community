// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
fun Int.foo() {}

fun test() {
    1.foo(<caret>)
}