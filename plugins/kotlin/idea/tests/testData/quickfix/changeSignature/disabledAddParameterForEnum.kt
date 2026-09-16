// "Add parameter to function 'values'" "false"
// DISABLE_ERRORS

enum class A {}
fun a() {
    A.values("a<caret>bc")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix