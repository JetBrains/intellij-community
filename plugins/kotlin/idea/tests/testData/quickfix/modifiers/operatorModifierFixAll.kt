// "Apply all 'Add modifier' fixes in file" "true"
// K2_ERROR: OPERATOR_MODIFIER_REQUIRED
// K2_ERROR: OPERATOR_MODIFIER_REQUIRED
class A {
    fun plus(a: A): A = A()
    fun minus(a: A): A = A()
}

fun foo() {
    A() <caret>+ A()
    A() - A()
}

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems