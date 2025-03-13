// "Make 'a' 'private'" "true"
class A(<caret>internal val a: String = "") {
    fun foo() {
        a
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix