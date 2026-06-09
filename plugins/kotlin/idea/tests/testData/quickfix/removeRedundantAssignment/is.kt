// "Remove redundant assignment" "true"
fun test(number: Number) {
    var isInt: Boolean
    <caret>isInt = number is Int
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix