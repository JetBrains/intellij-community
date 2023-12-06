// "Change type of base property 'A.x' to 'String'" "true"
interface A {
    var x: Int
}

interface B {
    var x: String
}

interface C : A, B {
    override var x: String<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$ForOverridden
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix