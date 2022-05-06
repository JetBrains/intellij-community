// IS_APPLICABLE: true
// AFTER-WARNING: Variable 'q' is never used
fun foo() {
    val s = ""
    val q<caret> = s
}