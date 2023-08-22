// "Terminate preceding call with semicolon" "true"

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