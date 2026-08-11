// "Rename to _" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.diagnosticBased.UnusedVariableInspection
data class A(val x: String, val y: Int)

fun bar(z: List<A>) {
    for ((x<caret>, y) in z) {
        y.hashCode()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RenameToUnderscoreFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.RemoveUnusedVariableFix