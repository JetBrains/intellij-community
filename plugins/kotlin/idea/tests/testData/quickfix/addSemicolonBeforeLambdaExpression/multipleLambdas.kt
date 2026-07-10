// "Terminate preceding call with semicolon" "true"
// K2_ERROR: UNEXPECTED_TRAILING_LAMBDA_ON_A_NEW_LINE
// K2_ERROR: UNEXPECTED_TRAILING_LAMBDA_ON_A_NEW_LINE

fun foo(
    fn: () -> Unit
) {}

fun test() {
    foo()
    {}
    {}<caret>
    {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix