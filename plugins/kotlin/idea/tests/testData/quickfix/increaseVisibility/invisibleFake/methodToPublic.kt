// "Make 'doSth' public" "true"
// PRIORITY: HIGH

open class A {
    private fun doSth() {
    }
}

class B : A() {
    fun bar() {
        <caret>doSth()
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToPublicFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction