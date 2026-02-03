// LANGUAGE_VERSION: 1.6
// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo() {
    +0.0f <caret>== +0.0f
}