// "Add parameter to function 'bar'" "true"
// DISABLE_ERRORS
fun bar(isObject: Boolean) {}

fun test(isObject: Boolean) {
    bar(true, isObject<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix