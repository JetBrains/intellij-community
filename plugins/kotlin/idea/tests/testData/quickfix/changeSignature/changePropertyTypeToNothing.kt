// "Specify 'Nothing' type for enclosing property 'Test.bar'" "true"
// K2_ERROR: IMPLICIT_NOTHING_PROPERTY_TYPE

class Test {
    val ba<caret>r = TODO()
}


// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix