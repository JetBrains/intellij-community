// "Change type of base property 'A.x' to 'String'" "true"
// K2_ERROR: Type of 'var x: String' doesn't match the type of the overridden 'var' property 'var x: Int' defined in 'A'.
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