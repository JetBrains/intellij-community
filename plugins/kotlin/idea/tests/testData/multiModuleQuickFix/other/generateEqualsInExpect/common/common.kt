// "Generate 'equals()'" "true"
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.EqualsOrHashCodeInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.EqualsOrHashCodeInspection

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun hashCode(): Int
}