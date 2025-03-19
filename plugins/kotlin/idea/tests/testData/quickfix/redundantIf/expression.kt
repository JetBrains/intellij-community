// "Remove redundant 'if' statement" "true"
fun bar(value: Int): Boolean {
    val x = <caret>if (value % 2 == 0) false else true
    return x
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf