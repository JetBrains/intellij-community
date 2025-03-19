// "Change the signature of lambda expression" "true"
// DISABLE_ERRORS

fun f(x: Int, y: Int, z : () -> Int) {
    f(1, 2, {x: Int, y: Int<caret> -> 42});
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionLiteralSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix