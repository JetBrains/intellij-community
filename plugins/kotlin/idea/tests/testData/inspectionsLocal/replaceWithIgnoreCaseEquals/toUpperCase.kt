// WITH_STDLIB
// ERROR: Using 'toUpperCase(): String' is an error. Use uppercase() instead.
// ERROR: Using 'toUpperCase(): String' is an error. Use uppercase() instead.
fun test(a: String, b: String): Boolean {
    return <caret>a.toUpperCase() == b.toUpperCase()
}