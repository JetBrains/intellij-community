// "Add non-null asserted (!!) call" "true"

fun foo(a: Array<String?>): String {
    return <caret>a[0]
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix