// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection

expect fun runAction(block: () -> Unit): Any?<caret>