// "Change type arguments to <*>" "true"
fun test(a: Any) {
    foo(a as List<Boolean><caret>)
}

fun foo(list: List<*>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToStarProjectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeToStarProjectionFixFactory$ChangeToStarProjectionFix