// "Create local variable 'foo'" "false"
// ERROR: Unresolved reference: @foo
// K2_AFTER_ERROR: Unresolved label.
fun refer() {
    val v = this@<caret>foo
}