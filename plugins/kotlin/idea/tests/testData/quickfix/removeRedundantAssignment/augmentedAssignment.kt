// "Remove redundant assignment" "true"
fun foo() = 1

fun test() {
    var i = 0
    <caret>i += foo()
}
// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix