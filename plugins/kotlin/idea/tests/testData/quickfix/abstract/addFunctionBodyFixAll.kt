// "Apply all 'Add function body' fixes in file" "true"
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY
// K2_ERROR: NON_ABSTRACT_FUNCTION_WITH_NO_BODY

class A {
    fun <caret>foo()
    fun bar()
}

// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
