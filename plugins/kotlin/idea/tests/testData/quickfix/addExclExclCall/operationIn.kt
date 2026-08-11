// "Add non-null asserted (a!!) call" "true"
// WITH_STDLIB
// K2_ERROR: UNSAFE_OPERATOR_CALL

fun foo(a: List<String>?) {
    "x" <caret>in a
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix