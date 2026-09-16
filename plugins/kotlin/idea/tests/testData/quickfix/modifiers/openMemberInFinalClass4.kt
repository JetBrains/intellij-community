// "Make 'A' 'open'" "true"
@Deprecated("") class A() {
    <caret>open fun foo() {}
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp