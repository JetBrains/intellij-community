// "Apply all 'Add function body' fixes in file" "true"
// K2_ERROR: Function 'bar' without a body must be abstract.
// K2_ERROR: Function 'foo' without a body must be abstract.

class A {
    fun <caret>foo()
    fun bar()
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
