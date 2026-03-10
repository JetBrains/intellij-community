// "Terminate preceding call with semicolon" "true"
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'R'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Cannot infer type for type parameter 'T'. Specify it explicitly.
// K2_ERROR: Expression is treated as a trailing lambda argument; consider separating it from the call with semicolon.
// K2_ERROR: No value passed for parameter 'value'.
// K2_ERROR: Unresolved reference 'invoke'.

fun foo() {}

fun test {
    foo()
    // comment and formatting
    { { { foo() }<caret> } }()()()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSemicolonBeforeLambdaExpressionFix