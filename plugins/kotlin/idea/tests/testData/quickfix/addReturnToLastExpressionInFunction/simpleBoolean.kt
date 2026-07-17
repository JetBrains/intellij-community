// "Add 'return' to last expression" "true"
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY
fun test(): Boolean {
    true
}<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix