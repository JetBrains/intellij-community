// WITH_STDLIB
fun main(){
    listOf<caret>(1, 2, 3, "4")
}

// K1 shows this diagnostic only in the beginning and the end of expressions
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.IterateExpressionIntention