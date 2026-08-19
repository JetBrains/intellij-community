// "Remove unused destructuring entry" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete

data class Point(val x: Int, val y: Int)

fun bar(p: Point) {
    (val x, val <caret>y) = p
    x.hashCode()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix
