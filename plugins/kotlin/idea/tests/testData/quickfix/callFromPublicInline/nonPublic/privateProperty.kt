// "Make 'foo' public" "true"
// K2_ERROR: Public-API inline function cannot access non-public-API property.
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