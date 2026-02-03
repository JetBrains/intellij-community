// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    ((y && true) || false) <caret>&& (true && (y && (y && (y ||false))))
}