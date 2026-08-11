// "Replace with safe (?.) call" "true"
// K2_ERROR: UNSAFE_IMPLICIT_INVOKE_CALL

fun foo(exec: (() -> Unit)?) = exec<caret>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix