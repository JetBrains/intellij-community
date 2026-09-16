// WITH_STDLIB

fun foo() {
    f()<caret>
}

fun f(): List<Int> = emptyList()

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.intentions.IterateExpressionIntention