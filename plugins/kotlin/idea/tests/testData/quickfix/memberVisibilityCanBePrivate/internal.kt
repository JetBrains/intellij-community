// "Make 'a' 'private'" "true"
class A {
    <caret>internal val a = ""

    fun foo() {
        a
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10