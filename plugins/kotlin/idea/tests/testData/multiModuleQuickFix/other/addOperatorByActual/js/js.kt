// "Add 'operator' modifier" "true"
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.AddOperatorModifierInspection
// K2_TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.AddOperatorModifierInspection

actual class Foo {
    actual fun <caret>unaryMinus() {

    }
}