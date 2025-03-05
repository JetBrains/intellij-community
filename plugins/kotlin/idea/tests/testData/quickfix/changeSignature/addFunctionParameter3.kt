// "Add parameter to function 'called'" "true"
// WITH_STDLIB
// DISABLE_ERRORS

fun caller() {
    called(<caret>setOf(1, 2, 3))
}

fun called() {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix