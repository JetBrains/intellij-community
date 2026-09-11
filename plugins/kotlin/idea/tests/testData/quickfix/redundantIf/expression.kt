// "Remove redundant 'if' statement" "true"
fun bar(value: Int): Boolean {
    val x = <caret>if (value % 2 == 0) {
        value > 10 || value < -10
    } else {
        false
    }
    return x
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf