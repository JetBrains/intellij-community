// "Remove parameter 'baz'" "true"
private fun foo(foo: String, bar: String, <caret>baz: String = "") {}

private fun test() {
    foo(
        "foo", // foo comment
        "bar", // bar comment
        "baz" // baz comment
    )
}