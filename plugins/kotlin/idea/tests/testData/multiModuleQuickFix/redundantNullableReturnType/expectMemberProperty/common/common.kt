// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection

expect class MyClass {
    val prop: Any?<caret>
}