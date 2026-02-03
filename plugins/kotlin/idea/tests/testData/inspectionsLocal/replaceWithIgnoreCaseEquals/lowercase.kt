// WITH_STDLIB
// IGNORE_K1
fun test(a: String, b: String): Boolean {
    return <caret>a.lowercase() == b.lowercase()
}