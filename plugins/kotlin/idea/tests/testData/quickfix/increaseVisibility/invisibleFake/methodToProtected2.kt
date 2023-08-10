// "Make 'doSth' protected" "true"

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