// "Add 'return' to last expression" "true"
// WITH_STDLIB
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

fun foo(): Any {
    true
}<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix