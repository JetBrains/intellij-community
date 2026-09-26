// "class org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix" "false"
//ERROR: Too many arguments for public final operator fun component1(): Int defined in Data
// K2_ERROR: TOO_MANY_ARGUMENTS
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS

data class Data(val i: Int) {}

fun usage(d: Data) {
    d.component1(<caret>2)
}
