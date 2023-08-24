// "Change 'pairs' to '*pairs'" "true"
// WITH_STDLIB

fun myMapOf(vararg pairs: Pair<String,String>) {
    val myMap = mapOf(<caret>pairs)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix