// "Change type to 'CharSequence'" "true"
// K2_ERROR: PROPERTY_TYPE_MISMATCH_ON_OVERRIDE
interface A {
    val x: CharSequence
}

class B(override val x: Any<caret>) : A

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVariableTypeFix$OnType
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix