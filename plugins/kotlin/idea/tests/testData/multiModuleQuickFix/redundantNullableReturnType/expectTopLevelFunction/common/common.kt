// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection
// IGNORE_K2

expect fun runAction(block: () -> Unit): Any?<caret>