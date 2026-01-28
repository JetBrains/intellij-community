// "class org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix" "false"
//ERROR: Too many arguments for public final operator fun component1(): Int defined in Data
// K2_AFTER_ERROR: Too many arguments for 'fun component1(): Int'.

data class Data(val i: Int) {}

fun usage(d: Data) {
    d.component1(<caret>2)
}
