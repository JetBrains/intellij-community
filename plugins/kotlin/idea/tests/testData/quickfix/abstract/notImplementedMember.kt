// "Make 'A' 'abstract'" "true"
interface I {
    fun foo()
}

<caret>class A : I {
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix