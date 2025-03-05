// INTENTION_TEXT: Convert to vararg parameter (may break code)
// DISABLE_ERRORS
fun test(a: Array<String><caret>) {
    a[0] = ""
}