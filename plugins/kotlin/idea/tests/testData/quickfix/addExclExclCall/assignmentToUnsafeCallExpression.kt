// "Add non-null asserted (!!) call" "true"
class A(var s: String)

fun foo(a: A?) {
    a<caret>.s = ""
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddExclExclCallFix