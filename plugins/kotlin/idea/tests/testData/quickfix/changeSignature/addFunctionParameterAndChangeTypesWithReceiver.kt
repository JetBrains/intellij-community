// "Change the signature of function 'withReceiver'" "true"

fun String.withReceiver(i: Int) {}

private fun test(s: String, q: Boolean) {
    s.withReceiver(q, <caret>2)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$applicator$1