// "Implement members" "true"
// WITH_STDLIB
// DISABLE-ERRORS
interface A {
    fun foo() {}
    fun bar() {}
}

open class B {
    open fun foo() {}
    open fun bar() {}
}

class<caret> C : A, B()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler