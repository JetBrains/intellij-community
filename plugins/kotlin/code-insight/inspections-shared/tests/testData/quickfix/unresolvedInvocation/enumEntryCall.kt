// "Remove invocation" "true"

enum class Test {
    A
}

fun test() {
    Test.A<caret>()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix$RemoveInvocationQuickFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.UnresolvedInvocationQuickFix$RemoveInvocationQuickFix