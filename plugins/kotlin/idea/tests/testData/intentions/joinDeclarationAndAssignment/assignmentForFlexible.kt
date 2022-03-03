// IS_APPLICABLE: true
// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
fun foo() {
    val x: String<caret>
    x = System.getProperty("")
}