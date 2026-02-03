// PROBLEM: none
// WITH_STDLIB
private fun bool(a: Int, b: Int): Boolean {
    if (a > 0) throw Exception("")
    if (a + b > 0) return true
    println(a - b)
    ret<caret>urn false
}