// "Remove redundant assignment" "true"
fun test(number: Number) {
    var isInt: Boolean
    <caret>isInt = number is Int
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix