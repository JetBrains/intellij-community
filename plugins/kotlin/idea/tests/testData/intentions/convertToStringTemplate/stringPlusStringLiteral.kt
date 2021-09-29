// AFTER-WARNING: Variable 'nv2' is never used
fun foo() {
    val prefix = "prefix"
    val nv2 = prefix + <caret>"postfix"
}
