// PRIORITY: LOW
// ERROR: Unresolved reference: X
// SKIP_ERRORS_AFTER
// K2_ERROR: SYNTAX
// K2_ERROR: UNRESOLVED_REFERENCE

fun foo(n: Int) {
    <caret>var x = X<>()
}
