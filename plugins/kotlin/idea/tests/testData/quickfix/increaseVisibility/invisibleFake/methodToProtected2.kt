// "Make 'doSth' protected" "true"
// K2_ERROR: Cannot access 'fun doSth(): Unit': it is private in 'A'.

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