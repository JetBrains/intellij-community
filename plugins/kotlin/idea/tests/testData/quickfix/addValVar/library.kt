// "Change to 'val'" "false"
// WITH_STDLIB
// DISABLE_ERRORS
// IGNORE_IRRELEVANT_ACTIONS
fun foo() {
    "a".length<caret> = 1
}