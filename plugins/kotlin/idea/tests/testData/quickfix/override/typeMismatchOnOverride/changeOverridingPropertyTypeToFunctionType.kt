// "Change type to '(String) -> Int'" "true"
// K2_ERROR: Type of 'var x: (Int) -> String' doesn't match the type of the overridden 'var' property 'var x: (String) -> Int' defined in 'A'.
interface A {
    var x: (String) -> Int
}
interface B : A {
    override var x: (Int) -> String<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix