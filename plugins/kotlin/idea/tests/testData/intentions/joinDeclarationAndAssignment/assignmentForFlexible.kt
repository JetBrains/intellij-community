// IS_APPLICABLE: true
// WITH_RUNTIME
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x: String<caret>
    x = System.getProperty("")
}