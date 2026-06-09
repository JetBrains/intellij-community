// "Rename to _" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
// ACTION: Convert parameter to receiver
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Remove parameter 'x'

fun foo(x<caret>: Int) {

}
