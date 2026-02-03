// "class org.jetbrains.kotlin.idea.quickfix.ChangeFunctionSignatureFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ChangeSignatureFixFactory$ParameterQuickFix" "false"
//ERROR: No value passed for parameter 'other'
// K2_AFTER_ERROR: No value passed for parameter 'other'.

abstract class StringComparable {
    public fun compareTo(other: String): Int = 0
}

class X: Comparable<String>, StringComparable()

fun main(args: Array<String>) {
    X().compareTo(<caret>)
}