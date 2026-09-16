fun foo(names: List<String>) {
    names<caret>
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.intentions.IterateExpressionIntention