// "Change type of base property 'B.x' to '(Int) -> Int'" "true"
// K2_ERROR: Type of 'val x: (Int) -> Int' is not a subtype of overridden property 'val x: (String) -> Any' defined in 'B'.
interface A {
    val x: (Int) -> Int
}

interface B {
    val x: (String) -> Any
}

interface C : A, B {
    override val x: (Int) -> Int<caret>
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$ForOverridden
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix