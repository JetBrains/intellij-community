// "Implement members" "true"
// WITH_STDLIB
interface I {
    fun foo()
}

data <caret>class C(val i: Int) : I
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix