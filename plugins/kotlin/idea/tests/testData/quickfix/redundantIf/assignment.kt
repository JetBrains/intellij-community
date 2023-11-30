// "Remove redundant 'if' statement" "true"
fun bar(p: Int) {
    var v2 = false
    <caret>if (p > 0) {
        v2 = false
    }
    else {
        v2 = true
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf