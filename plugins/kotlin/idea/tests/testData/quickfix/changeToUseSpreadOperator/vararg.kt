// "Change 'y' to '*y'" "true"

fun foo(vararg x: String) {}

fun bar(vararg y: String) = foo(y<caret>)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeToUseSpreadOperatorFix