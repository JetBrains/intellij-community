// "Rename 'e' to '_'" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection

fun parse(v: String): Int {
    try {
        return v.toInt()
    } catch (<caret>e: NumberFormatException) {
        return 0
    }
}

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix