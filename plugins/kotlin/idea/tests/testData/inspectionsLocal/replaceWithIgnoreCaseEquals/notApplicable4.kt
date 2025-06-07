// PROBLEM: none
// WITH_STDLIB
// ERROR: Using 'toLowerCase(): String' is an error. Use lowercase() instead.
// ERROR: Using 'toUpperCase(): String' is an error. Use uppercase() instead.
// K2_ERROR: 'fun String.toLowerCase(): String' is deprecated. Use lowercase() instead.
// K2_ERROR: 'fun String.toUpperCase(): String' is deprecated. Use uppercase() instead.
fun test(a: String, b: String): Boolean {
    return <caret>a.toLowerCase() == b.toUpperCase()
}