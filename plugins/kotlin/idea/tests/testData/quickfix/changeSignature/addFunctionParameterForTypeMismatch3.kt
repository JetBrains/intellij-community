// "Add 3rd parameter to function 'foo'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS
fun foo(i1: Int, i2: Int, i3: Int, i4: Int) {
}

fun test() {
    foo(1, 2, <caret>"", 4, 5)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix