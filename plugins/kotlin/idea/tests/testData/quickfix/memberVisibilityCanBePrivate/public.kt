// "Make 'a' 'private'" "true"
class A {
    <caret>public val a = ""

    fun foo() {
        a
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10