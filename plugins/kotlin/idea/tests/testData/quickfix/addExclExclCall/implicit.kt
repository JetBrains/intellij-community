// "Add non-null asserted (length!!) call" "true"
// K2_ERROR: UNSAFE_CALL

fun String?.foo() {
    <caret>length
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix