// "Change the signature of constructor 'FooBar'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'String' was expected.
// K2_ERROR: Too many arguments for 'constructor(name: String): FooBar'.

private class FooBar(val name: String)
fun test() {
    val foo = FooBar(1, <caret>"name")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix