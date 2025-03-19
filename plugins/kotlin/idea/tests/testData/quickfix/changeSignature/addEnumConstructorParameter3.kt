// "Add parameter to constructor 'Foo'" "true"
// DISABLE_ERRORS
enum class Foo(n: Int) {
    A(1, 2<caret>),
    B(3),
    C(3, 4),
    D(),
    E
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix