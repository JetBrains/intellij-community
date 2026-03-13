// "Add 'return' to last expression" "true"
// WITH_STDLIB
// K2_ERROR: Missing return statement.

fun foo(): Any {
    true
}<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix