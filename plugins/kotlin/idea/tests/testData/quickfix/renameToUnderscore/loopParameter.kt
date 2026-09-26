// "Rename 'value' to '_'" "true"
// TOOL: org.jetbrains.kotlin.idea.codeInsight.inspections.UnusedSymbolInspection
// COMPILER_ARGUMENTS: -Xreturn-value-checker=check

fun foo(){}

fun bar() {
    for (value<caret> in 1..3) {
        foo()
    }
}

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.quickfix.RenameElementFix