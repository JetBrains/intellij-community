// "Make 'B' 'open'" "true"
// K2_ERROR: This type is final, so it cannot be extended.
class A {
    class B
}

class C : <caret>A.B()
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp