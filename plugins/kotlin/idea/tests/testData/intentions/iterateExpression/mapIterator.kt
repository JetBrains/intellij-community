// WITH_STDLIB
// AFTER-WARNING: Variable 'i' is never used
// AFTER-WARNING: Variable 's' is never used
fun test() {
    mapOf(1 to "1", 2 to "2")<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.IterateExpressionIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.IterateExpressionIntention