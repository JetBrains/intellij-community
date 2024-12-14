// "Add parameter to function 'values'" "false"
// DISABLE-ERRORS

enum class A {}
fun a() {
    A.values("a<caret>bc")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix