// "Remove unused destructuring entry" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

fun bar(p: Pair<Int, Int>) {
    (val <caret>first, val second) = p
    second.hashCode()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix
