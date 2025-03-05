// LANGUAGE_VERSION: 1.4
// DISABLE_ERRORS

fun testValLabelInReturn() {
    <caret>L@ val fn = { return@L }
    fn()
}