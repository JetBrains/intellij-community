// "Add 'operator' modifier" "true"
// TOOL: org.jetbrains.kotlin.idea.inspections.AddOperatorModifierInspection
// IGNORE_K2

actual class Foo {
    actual fun <caret>unaryMinus() {

    }
}