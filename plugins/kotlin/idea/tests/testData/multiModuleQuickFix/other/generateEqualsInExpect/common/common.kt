// "Generate 'equals()'" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.EqualsOrHashCodeInspection
// IGNORE_K2

expect class With<caret>Constructor(x: Int, s: String) {
    val x: Int
    val s: String

    override fun hashCode(): Int
}