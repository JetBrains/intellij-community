// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun foo(b: Boolean): Boolean {
    <caret>if (b) return false // comment

    return true
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf