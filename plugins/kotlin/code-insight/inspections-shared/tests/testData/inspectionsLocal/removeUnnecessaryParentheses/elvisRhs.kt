// PROBLEM: none
// WITH_STDLIB
fun foo(): Boolean {
    return <caret>("" ?: return false) in listOf("")
}