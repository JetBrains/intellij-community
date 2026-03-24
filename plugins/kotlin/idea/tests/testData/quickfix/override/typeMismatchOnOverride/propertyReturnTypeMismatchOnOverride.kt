// "Change type to 'Int'" "true"
// K2_ERROR: Type of 'val x: Number' is not a subtype of overridden property 'val x: Int' defined in 'X'.
interface X {
    val x: Int
}

class A : X {
    override val x: Number<caret> = 42
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix