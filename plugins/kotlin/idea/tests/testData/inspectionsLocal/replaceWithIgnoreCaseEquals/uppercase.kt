// IGNORE_K1
// WITH_STDLIB
fun test(a: String, b: String): Boolean {
    return <caret>a.uppercase() == b.uppercase()
}