// "Apply all 'Add modifier' fixes in file" "true"
class A {
    fun xyzzy(i: Int) {}
    fun foobar(i: Int) {}
}

fun foo() {
    A() <caret>xyzzy 5
    A() foobar 5
}

// FUS_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems
// FUS_K2_QUICKFIX_NAME: com.intellij.codeInsight.daemon.impl.actions.FixAllHighlightingProblems