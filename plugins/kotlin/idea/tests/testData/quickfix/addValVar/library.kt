// "Change to 'val'" "false"
// WITH_STDLIB
// DISABLE_ERRORS
// IGNORE_IRRELEVANT_ACTIONS
// K2_AFTER_ERROR: 'val' cannot be reassigned.
fun foo() {
    "a".length<caret> = 1
}