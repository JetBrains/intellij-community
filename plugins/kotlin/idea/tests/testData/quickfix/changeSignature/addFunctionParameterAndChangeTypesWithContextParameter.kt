// "Change the signature of function 'bar'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(s: String)
fun bar(i: Int) {}

context(s: String)
fun test(q: Boolean) {
    bar(q, <caret>2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix