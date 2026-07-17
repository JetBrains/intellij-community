// "Change 'y' to '*y'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(vararg x: String) {}

fun bar(vararg y: String) = foo(y<caret>)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix