// FIX: Simplify boolean expression
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    (false)<caret> && true
}