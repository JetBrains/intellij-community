// "Change to 'val'" "false"
// WITH_STDLIB
// DISABLE-ERRORS
// IGNORE_IRRELEVANT_ACTIONS
fun foo() {
    "a".length<caret> = 1
}