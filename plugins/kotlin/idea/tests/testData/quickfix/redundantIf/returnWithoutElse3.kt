// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun test(a: Boolean, b: Boolean): Boolean {
    <caret>if (!a && b) {
        return false
    }

    return true
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf