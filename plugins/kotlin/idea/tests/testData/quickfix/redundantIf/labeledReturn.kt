// "Remove redundant 'if' statement" "true"
// WITH_STDLIB
fun foo() {
    listOf(1,2,3).find {
        <caret>if (it > 0) {
            return@find true
        } else {
            return@find false
        }
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf