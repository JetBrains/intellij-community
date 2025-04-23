// "Change type of enclosing property 'Test.bar' to 'Nothing'" "true"

class Test {
    val ba<caret>r = TODO()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix