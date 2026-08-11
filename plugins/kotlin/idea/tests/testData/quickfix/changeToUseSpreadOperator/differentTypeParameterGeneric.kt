// "Change 'y' to '*y'" "false"
// K2_AFTER_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_AFTER_ERROR: CANNOT_INFER_PARAMETER_TYPE
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: CANNOT_INFER_PARAMETER_TYPE

fun <T> foo(vararg x: Pair<List<String>, Pair<T, Number>>) {}

fun bar(y: Array<Pair<List<String>, Pair<Int, String>>>) = foo(<caret>y)


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix