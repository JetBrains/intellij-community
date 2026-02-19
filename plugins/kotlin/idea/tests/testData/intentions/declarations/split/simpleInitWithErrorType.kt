// PRIORITY: LOW
// ERROR: Unresolved reference: X
// SKIP_ERRORS_AFTER
// K2_ERROR: Syntax error: Incomplete code.
// K2_ERROR: Unresolved reference 'X'.

fun foo(n: Int) {
    <caret>var x = X<>()
}
