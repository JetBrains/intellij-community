// "Add non-null asserted (!!) call" "true"
// WITH_STDLIB
var i = 0

fun foo(s: String?) {
    i = s<caret>.length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix