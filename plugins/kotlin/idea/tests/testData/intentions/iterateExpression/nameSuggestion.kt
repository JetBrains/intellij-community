fun foo(names: List<String>) {
    names<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.intentions.IterateExpressionIntention