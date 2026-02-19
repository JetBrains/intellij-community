// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun foo(bar: Int?): Boolean {
    if (bar == 3) { return true }
    <caret>if (bar == null) { return false }
    // A comment

    /**
     * And more comment
     */

    return true
    bar?.let{ it + 4 }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf