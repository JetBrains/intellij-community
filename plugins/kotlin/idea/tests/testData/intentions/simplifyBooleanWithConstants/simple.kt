// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x = <caret>true && false || true
}