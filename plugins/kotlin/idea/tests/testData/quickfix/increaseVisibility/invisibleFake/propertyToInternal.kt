// "Make 'foo' internal" "true"
// K2_ERROR: Cannot access 'val foo: Int': it is private in 'A'.

open class A {
    private val foo = 1
}

class B : A() {
    fun bar() {
        <caret>foo
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix$ChangeToInternalFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToInternalModCommandAction