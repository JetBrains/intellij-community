// "Remove parameter 's'" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
fun test(i: Int) {
    val p: (String) -> Boolean =
        if (i == 1) { { <caret>s -> true } } else { s -> false }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSingleLambdaParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix