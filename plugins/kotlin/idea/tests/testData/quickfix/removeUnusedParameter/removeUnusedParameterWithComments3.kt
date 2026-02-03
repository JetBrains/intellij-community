// "Remove parameter 'bar'" "true"
private fun foo(foo: String, <caret>bar: String, baz: String = "") {}

private fun test() {
    foo(
        "foo", // foo comment
        "bar", // bar comment
        "baz" // baz comment
    )
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUnusedFunctionParameterFix