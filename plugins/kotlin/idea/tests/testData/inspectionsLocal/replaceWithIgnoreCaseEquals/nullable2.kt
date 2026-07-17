// WITH_STDLIB
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// K2_ERROR: DEPRECATION_ERROR
// K2_ERROR: DEPRECATION_ERROR
fun test(a: String, b: String?): Boolean {
    return <caret>a.toLowerCase() == b?.toLowerCase()
}