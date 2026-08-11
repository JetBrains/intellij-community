// "Terminate preceding call with semicolon" "true"
// K2_ERROR: UNEXPECTED_TRAILING_LAMBDA_ON_A_NEW_LINE
// K2_ERROR: UNRESOLVED_REFERENCE

fun doSomething() {}
fun Any.foo() {}

fun test() {
    doSomething().foo()
    // comment and formatting
    { doSomething()<caret> }()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix