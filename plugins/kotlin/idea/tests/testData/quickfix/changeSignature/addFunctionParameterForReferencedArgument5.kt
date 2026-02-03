// "Add parameter to function 'foo'" "true"
// DISABLE_ERRORS
fun foo() {}

class Test {
    val x: String = ""
        get() {
            foo(field<caret>)
            return field
        }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix