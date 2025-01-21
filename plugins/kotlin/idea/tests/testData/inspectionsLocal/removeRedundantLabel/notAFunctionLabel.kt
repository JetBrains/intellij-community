// K2_ERROR: Target label does not denote a function.
// K2_AFTER_ERROR: Unresolved label.

fun testValLabelInReturn() {
    <caret>L@ val fn = { return@L }
    fn()
}
