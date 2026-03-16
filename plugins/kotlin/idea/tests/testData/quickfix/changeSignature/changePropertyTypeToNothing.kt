// "Specify 'Nothing' type for enclosing property 'Test.bar'" "true"

class Test {
    val ba<caret>r = TODO()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix