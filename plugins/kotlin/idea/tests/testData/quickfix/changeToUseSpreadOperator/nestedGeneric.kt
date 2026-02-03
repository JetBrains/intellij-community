// "Change 'y' to '*y'" "true"
// WITH_STDLIB

fun <T> foo(vararg x: Pair<List<String>, Pair<T, Number>>) {}

fun bar(y: Array<Pair<ArrayList<String>, Pair<Int, Int>>>) = foo(<caret>y)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix