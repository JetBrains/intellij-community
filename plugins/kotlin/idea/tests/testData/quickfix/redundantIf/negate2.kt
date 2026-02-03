// "Remove redundant 'if' statement" "true"
fun bar(value: Int): Boolean {
    <caret>if (!(value % 2 == 0)) {
        return false
    }
    else {
        return true
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf