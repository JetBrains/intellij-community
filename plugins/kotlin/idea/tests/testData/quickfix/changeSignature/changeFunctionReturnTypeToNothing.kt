// "Change return type of enclosing function 'Test.foo' to 'Nothing'" "true"

class Test {
    fun fo<caret>o() = TODO()
}

// IGNORE_K1
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix