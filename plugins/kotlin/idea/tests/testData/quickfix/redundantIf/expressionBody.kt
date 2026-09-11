// "Remove redundant 'if' statement" "true"
fun bar(value: Int) = <caret>if (value % 2 == 0) true else value > 10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.RedundantIfInspectionBase$RemoveRedundantIf