// "Change the signature of function 'withReceiver'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Boolean', but 'Int' was expected.
// K2_ERROR: Too many arguments for 'fun String.withReceiver(i: Int): Unit'.

fun String.withReceiver(i: Int) {}

private fun test(s: String, q: Boolean) {
    s.withReceiver(q, <caret>2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix