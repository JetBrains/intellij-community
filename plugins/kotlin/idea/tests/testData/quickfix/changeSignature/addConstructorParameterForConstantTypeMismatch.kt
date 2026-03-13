// "Add 1st parameter to constructor 'Foo'" "true"
// K2_ERROR: Argument type mismatch: actual type is 'Int', but 'String' was expected.
// K2_ERROR: Too many arguments for 'constructor(name: String): Foo'.

class Foo(val name: String)

fun test() {
    val foo = Foo(<caret>1, "name")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix