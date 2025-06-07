// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo() {
    val x = true
    val y = false
    (((((x || false)) && y)) <caret>|| false)
}