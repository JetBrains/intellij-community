// "Add parameter to function 'plus'" "false"
// DISABLE-ERRORS

fun a() {
    1.plus(1, <caret>2)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix