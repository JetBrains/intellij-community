// "Add parameter to function 'foo'" "true"
// DISABLE_ERRORS
fun foo() {}

fun test(isObject: Boolean) {
    foo(isObject<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix