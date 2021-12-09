// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection

expect class MyClass {
    actual val prop: Any?<caret>
}