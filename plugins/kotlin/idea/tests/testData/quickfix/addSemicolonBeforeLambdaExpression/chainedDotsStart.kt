// "Terminate preceding call with semicolon" "true"

fun test() {
    "test".toString().toString().toString()
    {<caret>}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix