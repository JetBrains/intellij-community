// "Add non-null asserted (!!) call" "true"

fun foo(a: Array<String?>) {
    a[0]<caret>.length
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix