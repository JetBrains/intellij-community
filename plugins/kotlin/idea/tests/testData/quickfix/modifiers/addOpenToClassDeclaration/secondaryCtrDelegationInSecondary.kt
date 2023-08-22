// "Make 'B' 'open'" "true"
class B {
    constructor() {
    }
}

class A : <caret>B {
    constructor() {}
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFixFE10
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddModifierFix