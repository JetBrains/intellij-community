// "Change return type of enclosing function 'Test.foo' to 'Nothing'" "true"
// WITH_STDLIB

class Test {
    fun fo<caret>o() = throw NotImplementedError()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeCallableReturnTypeFix$ForEnclosing
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeTypeQuickFixFactories$UpdateTypeQuickFix