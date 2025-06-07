// "Add 'operator' modifier" "true"
// K1_TOOL: org.jetbrains.kotlin.idea.inspections.AddOperatorModifierInspection
// K2_TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.AddOperatorModifierInspection

actual class Foo {
    actual fun <caret>unaryMinus() {

    }
}