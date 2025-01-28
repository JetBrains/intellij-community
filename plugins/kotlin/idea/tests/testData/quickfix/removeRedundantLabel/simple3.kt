// IGNORE_K1
// "Remove redundant label" "true"
fun testValLabelInReturn() {
    L@ val fn = { return@L<caret> }
    fn()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveReturnLabelFix