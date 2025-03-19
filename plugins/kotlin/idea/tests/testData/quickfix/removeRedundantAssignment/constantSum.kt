// "Remove redundant assignment" "true"
fun test() {
    var i: Int
    <caret>i = 1 + 1
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix