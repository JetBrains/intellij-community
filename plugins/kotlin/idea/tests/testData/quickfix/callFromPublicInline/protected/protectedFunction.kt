// "Make 'protectedMethod' public" "true"
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
open class Foo {
    protected fun protectedMethod() {}

    inline fun inlineFun() {
        <caret>protectedMethod()
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction