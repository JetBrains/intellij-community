// ERROR: Unresolved reference: X
// SKIP_ERRORS_AFTER

fun foo(n: Int) {
    <caret>val x = X<>()
}
