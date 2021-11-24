// "Make 'bar' internal" "true"
class C {
    internal fun foo() = true

    inline fun bar(baz: () -> Unit) {
        if (<caret>foo()) {
            baz()
        }
    }
}