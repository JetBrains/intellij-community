// "Change 'y' to '*y'" "false"
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'Array<Pair<List<String>, Pair<Int, String>>>', but 'Pair<List<String>, Pair<uninferred T (of fun <T> foo), Number>>' was expected.
// K2_AFTER_ERROR: Cannot infer type for this parameter. Specify it explicitly.

fun <T> foo(vararg x: Pair<List<String>, Pair<T, Number>>) {}

fun bar(y: Array<Pair<List<String>, Pair<Int, String>>>) = foo(<caret>y)

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix