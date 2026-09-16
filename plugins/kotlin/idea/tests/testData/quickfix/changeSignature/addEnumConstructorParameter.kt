// "Add parameter to constructor 'Foo'" "true"
// DISABLE_ERRORS
enum class Foo {
    A("A"<caret>),
    B("B")
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix