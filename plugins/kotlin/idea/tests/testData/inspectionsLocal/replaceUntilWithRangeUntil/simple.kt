// LANGUAGE_VERSION: 1.8
// COMPILER_ARGUMENTS: -opt-in=kotlin.ExperimentalStdlibApi
// WITH_STDLIB
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ReplaceUntilWithRangeUntilInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.ReplaceUntilWithRangeUntilInspection
fun main() {
    0 u<caret>ntil 10
}