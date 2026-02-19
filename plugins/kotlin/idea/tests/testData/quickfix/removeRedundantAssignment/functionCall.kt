// "Remove redundant assignment" "true"
fun foo() = 1

fun test() {
    var i: Int
    <caret>i = foo()
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedValueFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.diagnosticBased.AssignedValueIsNeverReadInspection$RemoveRedundantAssignmentFix