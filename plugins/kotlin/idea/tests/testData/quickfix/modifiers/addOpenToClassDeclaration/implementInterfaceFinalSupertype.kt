// "Make 'A' 'open'" "true"
// K2_ERROR: This type is final, so it cannot be extended.
interface X {}
interface Y {}

class A {}
class B : X, A<caret>(), Y {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixMpp