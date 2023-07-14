// "Add 'return' before the expression" "true"
fun test(): Boolean {
    <caret>true
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix