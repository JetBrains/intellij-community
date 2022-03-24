// "Remove 'inline' modifier" "true"
class C {
    private var foo = false

    inline fun bar(baz: () -> Unit) {
        if (foo<caret>) {
            baz()
        }
    }
}