// "Change 'array' to '*array'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Array<String>', but 'String' was expected.

fun foo(a: String, vararg x: String, b: Int) {}

fun bar(array: Array<String>) = foo("aaa", array<caret>, b = 1)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix