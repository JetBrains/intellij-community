// "Add 'return' before the expression" "true"
// WITH_STDLIB
// K2_ERROR: NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY

fun foo(): Any {
    <caret>true
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix