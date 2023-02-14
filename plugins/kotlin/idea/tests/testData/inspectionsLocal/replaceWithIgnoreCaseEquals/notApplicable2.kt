// PROBLEM: none
// WITH_STDLIB
fun test(a: String, b: String): Boolean {
    return <caret>a == b.toLowerCase()
}