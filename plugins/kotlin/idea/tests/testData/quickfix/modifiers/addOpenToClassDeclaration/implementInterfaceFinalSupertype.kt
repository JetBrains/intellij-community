// "Make 'A' 'open'" "true"
interface X {}
interface Y {}

class A {}
class B : X, A<caret>(), Y {}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix