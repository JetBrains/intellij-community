// "Make 'A' 'open'" "true"
class A() {
    <caret>open fun foo() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix