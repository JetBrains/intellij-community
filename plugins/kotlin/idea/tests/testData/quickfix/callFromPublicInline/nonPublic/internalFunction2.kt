// "Make 'bar' internal" "true"
class C {
    internal fun foo() = true

    inline fun bar(baz: () -> Unit) {
        if (<caret>foo()) {
            baz()
        }
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeVisibilityFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.ChangeVisibilityFixFactories$ChangeToInternalModCommandAction