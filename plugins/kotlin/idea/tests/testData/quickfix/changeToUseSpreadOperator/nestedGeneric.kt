// "Change 'y' to '*y'" "true"
// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Array<Pair<ArrayList<String>, Pair<Int, Int>>>', but 'Pair<List<String>, Pair<uninferred T (of fun <T> foo), Number>>' was expected.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.

fun <T> foo(vararg x: Pair<List<String>, Pair<T, Number>>) {}

fun bar(y: Array<Pair<ArrayList<String>, Pair<Int, Int>>>) = foo(<caret>y)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix