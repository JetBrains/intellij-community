// LANGUAGE_VERSION: 1.6
// FIX: Simplify boolean expression
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    <caret>2 > 1
}