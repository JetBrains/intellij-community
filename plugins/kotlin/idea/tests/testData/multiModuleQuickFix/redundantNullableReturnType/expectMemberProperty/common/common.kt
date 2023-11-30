// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection
// IGNORE_K2

expect class MyClass {
    val prop: Any?<caret>
}