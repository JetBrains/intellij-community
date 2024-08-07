// "Implement members" "true"
// ERROR: Conflicting overloads: public open fun bar(): Unit defined in C, public open fun bar(): Unit defined in C
// ERROR: Conflicting overloads: public open fun bar(): Unit defined in C, public open fun bar(): Unit defined in C
// ERROR: Conflicting overloads: public open fun foo(): Unit defined in C, public open fun foo(): Unit defined in C
// ERROR: Conflicting overloads: public open fun foo(): Unit defined in C, public open fun foo(): Unit defined in C
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
// ERROR: Unresolved reference: TODO
interface A {
    fun foo() {}
    fun bar() {}
}

interface B {
    fun foo() {}
    fun bar() {}
}

class<caret> C : A, B
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix
