// WITH_STDLIB
class T<U, V>

operator fun <X> T<X, String>.iterator(): Iterator<X> = listOf<X>().iterator()

fun test() {
    T<Int, String>()<caret>
}

// IGNORE_K2
// Ignore because of KTIJ-32233 K2 Mode: Support .iter/.for postfix for operator extension fun

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.intentions.IterateExpressionIntention
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.intentions.IterateExpressionIntention