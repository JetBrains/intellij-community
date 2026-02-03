// "Remove redundant interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation

fun test() {
    <caret>$$$$$$$$"sample $$$$$$$${3 + 2} text"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RemoveRedundantInterpolationQuickFix