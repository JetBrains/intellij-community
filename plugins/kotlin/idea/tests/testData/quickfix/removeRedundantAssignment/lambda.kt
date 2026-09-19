// "Remove redundant assignment" "true"
fun test() {
    var lambda: () -> Int
    <caret>lambda = { -> 42 }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix