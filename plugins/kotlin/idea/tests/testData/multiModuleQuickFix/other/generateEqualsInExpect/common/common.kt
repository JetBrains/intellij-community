// "Generate 'equals()'" "true"
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.EqualsOrHashCodeInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.EqualsOrHashCodeInspection

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun hashCode(): Int
}