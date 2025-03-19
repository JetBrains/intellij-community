// "Reorder parameters" "true"
// WITH_STDLIB
fun foo(
    c1: String,
    a: List<List<String>> = listOf(listOf(b<caret>)),
    b: String,
) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.refactoring.changeSignature.quickFix.ReorderParametersFixFactory$ReorderParametersFix