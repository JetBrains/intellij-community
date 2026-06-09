// "Remove redundant assignment" "true"
fun test() {
    var i: Int
    <caret>i = 1 + 1
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix