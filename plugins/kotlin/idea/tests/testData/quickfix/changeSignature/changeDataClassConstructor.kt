// "Change the signature of constructor 'FooBar'" "true"
// K2_ERROR: ARGUMENT_TYPE_MISMATCH
// K2_ERROR: TOO_MANY_ARGUMENTS

private data class FooBar(val name: String)
fun test() {
    val foo = FooBar(1, <caret>"name")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix