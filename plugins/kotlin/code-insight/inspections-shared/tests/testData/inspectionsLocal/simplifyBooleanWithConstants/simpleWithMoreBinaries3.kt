// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    y || false && true || <caret>false || false || false || y && true
}