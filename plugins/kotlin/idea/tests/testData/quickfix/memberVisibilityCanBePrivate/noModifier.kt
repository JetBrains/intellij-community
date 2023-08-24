// "Make 'a' 'private'" "true"
class A {
    val <caret>a = ""

    fun foo() {
        a
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10