// "Add 'return' before the expression" "true"
// WITH_STDLIB
// K2_ERROR: Missing return statement.

fun foo(): Any {
    <caret>true
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix