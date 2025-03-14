// WITH_STDLIB
fun main(){
    listOf(1, 2, 3, "4"<caret>)
}

// K1 shows this diagnostic only in the beginning and the end of expressions
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.IterateExpressionIntention