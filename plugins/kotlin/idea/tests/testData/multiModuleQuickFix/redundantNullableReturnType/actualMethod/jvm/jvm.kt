// "Make not-nullable" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.RedundantNullableReturnTypeInspection
// ACTION: Convert to block body
// ACTION: Remove explicit type specification
// IGNORE_K2

actual class MyClass {
    actual fun runAction(block: () -> Unit): Any?<caret> = block()
}