// "Change type of base property 'A.x' to 'String'" "true"
// K2_ERROR: VAR_TYPE_MISMATCH_ON_OVERRIDE
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
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix