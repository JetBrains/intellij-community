// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: UNRESOLVED_LABEL
// K2_ERROR: UNRESOLVED_LABEL
fun refer() {
    val v = this@<caret>foo
}