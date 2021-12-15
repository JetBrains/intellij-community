// WITH_STDLIB
// WITHOUT_CUSTOM_LINE_INDENT_PROVIDER
fun foo() {
    val v = run {
        <caret>foo()
    }

    print(1)
}
