// "Remove redundant 'if' statement" "true"
fun test(a: Boolean, b: Boolean): Boolean {
    return <caret>if (!a && b) {
        // comment
        false
    } else {
        true
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf