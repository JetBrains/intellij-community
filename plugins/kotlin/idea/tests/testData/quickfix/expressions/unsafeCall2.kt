// "Add non-null asserted (!!) call" "true"
fun foo(a: Int?) {
    a<caret>.plus(1)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix