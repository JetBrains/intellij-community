// WITH_STDLIB
// API_VERSION: 1.9
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ReplaceUntilWithRangeUntilInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ReplaceUntilWithRangeUntilInspection
fun test(from: Short, to: Short) {
    from unti<caret>l to
}