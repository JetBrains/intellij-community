// "Implement members" "true"
// WITH_STDLIB
// DISABLE_ERRORS
interface A {
    fun foo() {}
    fun bar() {}
    fun baz()
}

open class B {
    open fun foo() {}
    open fun bar() {}
    open fun baz() {}
}

class<caret> C : A, B()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix