// "Make 'foo' not open" "true"
class A() {
    <caret>open fun foo() {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveModifierFixBase