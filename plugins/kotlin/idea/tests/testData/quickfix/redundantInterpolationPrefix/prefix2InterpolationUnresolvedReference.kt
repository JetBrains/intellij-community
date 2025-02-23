// "Remove redundant interpolation prefix" "false"
// COMPILER_ARGUMENTS: -Xmulti-dollar-interpolation
// K2_AFTER_ERROR: Unresolved reference 'unresolved'.

fun test() {
    <caret>$$"sample $$unresolved text"
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.RemoveRedundantInterpolationQuickFix