// "Make 'bar' private" "true"
class C {
    private var foo = false

    inline fun bar(baz: () -> Unit) {
        if (foo<caret>) {
            baz()
        }
    }
}