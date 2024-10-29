// "Add 'return' to last expression" "true"
fun test(): Boolean {
    true
}<caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToLastExpressionInFunctionFix