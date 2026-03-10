// "Add 1st parameter to function 'foo'" "true"
// K2_ERROR: Null cannot be a value of a non-null type 'String'.
// K2_ERROR: Too many arguments for 'fun foo(s: String): Unit'.

fun foo(s : String) = Unit
fun test() {
    foo(<caret>null, "")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix