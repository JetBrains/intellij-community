// "Terminate preceding call with semicolon" "true"

fun foo() {}

fun test() {
    foo()/*
        block
        comment
    */
    // comment
    {}<caret>
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix