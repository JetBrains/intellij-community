// "Terminate preceding call with semicolon" "true"

fun test() {
    "test".toString().toString().toString()
    {<caret>"test"}.invoke().toString().toString()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix