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