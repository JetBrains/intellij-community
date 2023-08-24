// "Remove parameter 'baz'" "true"
private fun foo(foo: String, bar: String, <caret>baz: String = "") {}

private fun test() {
    foo(
        "foo", // foo comment
        "bar", // bar comment
        "baz" // baz comment
    )
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix