// "Add non-null asserted (!!) call" "true"

fun callMe(p: String) {}

fun callIt(p: Any) {
    callMe(<caret>p as String?)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix