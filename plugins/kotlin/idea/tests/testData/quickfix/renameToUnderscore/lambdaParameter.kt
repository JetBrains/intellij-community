// "Rename 'x' to '_'" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
fun foo(block: (String, Int) -> Unit) {
    block("", 1)
}

fun bar() {
    foo { x<caret>: String, y: Int ->
        y.hashCode()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RenameToUnderscoreFix
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix