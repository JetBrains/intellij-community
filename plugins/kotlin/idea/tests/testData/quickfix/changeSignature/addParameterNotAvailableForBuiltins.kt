// "class org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix" "false"
// ERROR: Too many arguments for public open operator fun equals(other: Any?): Boolean defined in kotlin.Any
// K2_ERROR: TOO_MANY_ARGUMENTS
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS

fun f(d: Any) {
    d.equals("a", <caret>"b")
}
