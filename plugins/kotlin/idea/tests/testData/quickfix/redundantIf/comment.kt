// "Remove redundant 'if' statement" "true"
fun test(a: Boolean, b: Boolean): Boolean {
    <caret>if (!a && b) {
        // comment1
        // comment2
        return false
    }

    return true
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf