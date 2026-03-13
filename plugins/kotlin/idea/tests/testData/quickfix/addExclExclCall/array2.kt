// "Add non-null asserted (a[0]!!) call" "true"
// K2_ERROR: Return type mismatch: expected 'String', actual 'String?'.

fun foo(a: Array<String?>): String {
    return <caret>a[0]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix