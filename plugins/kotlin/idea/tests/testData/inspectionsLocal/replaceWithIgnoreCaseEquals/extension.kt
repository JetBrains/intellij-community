// WITH_STDLIB
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
fun String.test(s: String): Boolean {
    return <caret>toLowerCase() == s.toLowerCase()
}
