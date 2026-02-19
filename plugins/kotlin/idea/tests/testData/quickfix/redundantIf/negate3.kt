// "Remove redundant 'if' statement" "true"
operator fun String.not(): Boolean = false

fun bar(value: Int): Boolean {
    <caret>if (!"hello") {
        return false
    }
    else {
        return true
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf