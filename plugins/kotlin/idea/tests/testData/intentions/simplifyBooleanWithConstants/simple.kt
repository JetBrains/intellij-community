// INTENTION_TEXT: "Simplify boolean expression"
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = <caret>true && false || true
}