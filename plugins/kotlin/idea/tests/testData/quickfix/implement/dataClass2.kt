// "Implement members" "true"
// WITH_STDLIB
// K2_ERROR: Class 'C' is not abstract and does not implement abstract member:<br>fun foo(): Unit
interface I {
    fun foo()
}

data <caret>class C(val i: Int) : I {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.ImplementMembersHandler
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.core.overrideImplement.KtImplementMembersQuickfix