// "Add 'return' before the expression" "true"
// WITH_STDLIB

fun foo(): Any {
    <caret>true
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddReturnToUnusedLastExpressionInFunctionFix