// "Terminate preceding call with semicolon" "true"

fun foo() {}

fun test() {
    foo()
    {<caret>"test"}.invoke().toString().toString()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix