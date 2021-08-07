// "Remove parameter 'foo'" "true"
private fun foo(<caret>foo: String, bar: String, baz: String = "") {}

private fun test() {
    foo(
        "foo", // foo comment
        "bar", // bar comment
        "baz" // baz comment
    )
}