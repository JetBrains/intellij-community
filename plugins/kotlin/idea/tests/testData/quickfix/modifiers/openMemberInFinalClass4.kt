// "Make 'A' 'open'" "true"
@Deprecated("") class A() {
    <caret>open fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp