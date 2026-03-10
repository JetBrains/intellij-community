// "Make 'A' 'open'" "true"
// K2_ERROR: This type is final, so it cannot be extended.
class A(val v: Int)

class B : A<caret>(1)

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp