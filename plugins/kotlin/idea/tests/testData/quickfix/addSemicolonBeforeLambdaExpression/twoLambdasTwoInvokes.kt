// "Terminate preceding call with semicolon" "true"

fun foo() {
    { "first" }.invoke()
    // comment and formatting
    {<caret> "second" }.invoke()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix