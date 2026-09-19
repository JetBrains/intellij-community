// "Remove redundant assignment" "true"
fun foo() = 1

fun test() {
    var i = 0
    <caret>i += foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix