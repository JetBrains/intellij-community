// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection
// ACTION: Convert to block body
// ACTION: Do not show return expression hints
// ACTION: Remove explicit type specification

actual fun runAction(block: () -> Unit): Any?<caret> = block()