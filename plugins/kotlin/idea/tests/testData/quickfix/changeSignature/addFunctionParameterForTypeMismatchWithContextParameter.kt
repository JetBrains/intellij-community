// "Add 2nd parameter to function 'bar'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS
// COMPILER_ARGUMENTS: -Xcontext-parameters

context(s: String)
fun bar(a: Int, c: Char) {}

context(s: String)
fun test(flag: Boolean) {
    bar(1, <caret>flag, 'c')
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix