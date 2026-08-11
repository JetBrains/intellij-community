// "Terminate preceding call with semicolon" "true"
// K2_ERROR: UNEXPECTED_TRAILING_LAMBDA_ON_A_NEW_LINE

fun foo() {}

fun test {
    { { { foo() } } }()()()
    // comment and formatting
    {<caret>}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix