// "Remove parameter 'i'" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
fun test() {
    val f: (Int) -> Unit = { <caret>i: Int -> foo() }
    bar(f)
}

fun foo() {}
fun bar(f: (Int) -> Unit) {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveSingleLambdaParameterFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix