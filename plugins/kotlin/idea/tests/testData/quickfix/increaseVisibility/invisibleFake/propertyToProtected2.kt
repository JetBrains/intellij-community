// "Make 'foo' protected" "true"

open class A {
    private val foo = 1
}

open class B : A()

class C : B() {
    fun bar() {
        <caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToProtectedFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToProtectedModCommandAction