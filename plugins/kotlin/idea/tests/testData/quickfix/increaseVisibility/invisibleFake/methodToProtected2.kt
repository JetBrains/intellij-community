// "Make 'doSth' protected" "true"
// K2_ERROR: INVISIBLE_REFERENCE

open class A {
    private fun doSth() {
    }
}

open class B : A()

class C : B() {
    fun bar() {
        <caret>doSth()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToProtectedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToProtectedModCommandAction