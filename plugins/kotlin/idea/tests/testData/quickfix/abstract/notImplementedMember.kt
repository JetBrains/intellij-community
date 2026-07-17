// "Make 'A' 'abstract'" "true"
// K2_ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
interface I {
    fun foo()
}

<caret>class A : I {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp