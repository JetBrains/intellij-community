// "Make 'foo' public" "true"
// K2_ERROR: PROTECTED_CALL_FROM_PUBLIC_INLINE_ERROR
open class A {
    protected infix fun foo(p: Int) {
    }

    inline fun call() {
        A() foo<caret> 8
    }
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction