// "Change 'y' to '*y'" "false"

fun <T> foo(vararg x: Pair<List<String>, Pair<T, Number>>) {}

fun bar(y: Array<Pair<List<String>, Pair<Int, String>>>) = foo(<caret>y)

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix