// "Change the signature of lambda expression" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo(f: Int.(Int, Int) -> Int) {

}

fun test() {
    foo { <caret>a: Int -> 0 }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeFunctionLiteralSignatureFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix