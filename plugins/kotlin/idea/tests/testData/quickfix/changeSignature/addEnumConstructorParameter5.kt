// "Add parameter to constructor 'Foo'" "true"
// DISABLE_ERRORS
enum class Foo {
    A(1<caret>),
    B(2),
    C(),
    D,
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix