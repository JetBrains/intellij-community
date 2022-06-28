// "Safe delete 'foo'" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
// ACTION: Convert member to extension
// ACTION: Do not show return expression hints
// ACTION: Move to companion object

actual class My {
    actual fun <caret>foo() {}
}