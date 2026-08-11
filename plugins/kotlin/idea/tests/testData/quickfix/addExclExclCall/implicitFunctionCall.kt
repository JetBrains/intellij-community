// "Add non-null asserted (lowercase()!!) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_CALL
fun String?.foo() {
    <caret>lowercase()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix