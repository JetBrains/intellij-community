// "Safe delete 'foo'" "false"
// TOOL: org.jetbrains.kotlin.idea.inspections.UnusedSymbolInspection
// ACTION: Convert member to extension
// ACTION: Move to companion object
// IGNORE_K2

actual class My {
    actual fun <caret>foo() {}
}