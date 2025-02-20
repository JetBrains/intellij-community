// IGNORE_K1
// "Remove redundant label" "true"
// K2_AFTER_ERROR: 'return' is prohibited here.
fun testValLabelInReturn() {
    L@ val fn = { return@L<caret> }
    fn()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveReturnLabelFix