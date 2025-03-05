// LANGUAGE_VERSION: 1.4
// ERROR: Target label does not denote a function
// AFTER_ERROR: 'return' is not allowed here

fun testValLabelInReturn() {
    L@ val fn = { return@L<caret> }
    fn()
}