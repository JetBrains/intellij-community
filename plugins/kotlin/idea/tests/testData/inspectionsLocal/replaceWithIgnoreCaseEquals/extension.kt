// WITH_STDLIB
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// K2_ERROR: DEPRECATION_ERROR
// K2_ERROR: DEPRECATION_ERROR
fun String.test(s: String): Boolean {
    return <caret>toLowerCase() == s.toLowerCase()
}
