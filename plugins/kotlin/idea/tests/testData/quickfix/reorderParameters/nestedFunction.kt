// "Reorder parameters" "true"
fun foo(b: Int) {
    fun bar(
        a: Int = b<caret>,
        b: Int = 2
    ) = Unit
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReorderParametersFixFactory$ReorderParametersFix