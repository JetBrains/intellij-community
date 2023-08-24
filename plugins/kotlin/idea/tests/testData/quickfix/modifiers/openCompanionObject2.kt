// "Make 'Foo' not open" "true"
class A {
    <caret>open companion object Foo {
        fun a(): Int = 1
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase