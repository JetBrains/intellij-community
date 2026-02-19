// "Add non-null asserted (lowercase()!!) call" "true"
// WITH_STDLIB
fun String?.foo() {
    <caret>lowercase()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix