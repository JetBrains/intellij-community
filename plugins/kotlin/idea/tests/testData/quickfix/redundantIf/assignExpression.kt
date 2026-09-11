// "Remove redundant 'if' statement" "true"
fun bar(p: Int) {
    var v1 = false
    <caret>if (p > 0) v1 = false else v1 = p < -10
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf