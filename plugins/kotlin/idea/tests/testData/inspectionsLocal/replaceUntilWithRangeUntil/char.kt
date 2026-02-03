// WITH_STDLIB
// API_VERSION: 1.9
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceUntilWithRangeUntilInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.shared.ReplaceUntilWithRangeUntilInspection
fun test(from: Char, to: Char) {
    from unt<caret>il to
}