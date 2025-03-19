// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
fun foo(`some name`: Int) {}

fun test() {
    foo(<caret>)
}