// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Remove explicit type specification

actual val prop: Any?<caret> = 42