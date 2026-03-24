// "Replace with safe (?.) call" "true"
// K2_ERROR: Reference has a nullable type '(() -> Unit)?'. Use explicit '?.invoke' to make a function-like call instead.

fun foo(exec: (() -> Unit)?) = exec<caret>()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceInfixOrOperatorCallFix