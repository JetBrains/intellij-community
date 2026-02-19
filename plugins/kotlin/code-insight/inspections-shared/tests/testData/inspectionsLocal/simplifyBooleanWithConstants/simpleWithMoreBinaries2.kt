// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    false || false || y || y ||<caret> false || y && y || y
}