// "Rename to _" "false"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
// ACTION: Remove variable 'x'
// ACTION: Specify type explicitly
// ACTION: Split property declaration

fun bar() {
    val x<caret> = 1
}
