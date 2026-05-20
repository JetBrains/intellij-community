// "Change 'y' to '*y'" "false"
// K2_ERROR: Argument type mismatch: actual type is 'IntArray', but 'String' was expected.
// K2_AFTER_ERROR: Argument type mismatch: actual type is 'IntArray', but 'String' was expected.

fun foo(vararg x: String) {}

fun bar(vararg y: Int) = foo(<caret>y)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix