// "Make 'foo' public" "true"
class C {
    private var foo = false

    inline fun bar(baz: () -> Unit) {
        if (foo<caret>) {
            baz()
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToPublicModCommandAction