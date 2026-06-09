// "Remove redundant interpolation prefix" "true"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    <caret>$$$$$$$$"sample text"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.RemoveRedundantInterpolationQuickFix