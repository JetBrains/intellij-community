// "Add parameter to constructor 'AnnParClass'" "true"

private annotation class AnnParClass(val p1: Int, val p2: Int)
@AnnParClass(1, 2, <caret>3)
private val vac = 3

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$applicator$1