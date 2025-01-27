// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RedundantLabelInspection
// "Remove redundant label" "true"
fun foo() {
    <caret>L1@ val x = bar()
}

fun bar() {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveRedundantLabelFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.RedundantLabelInspection$createQuickFixes$1