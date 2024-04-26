// WITH_STDLIB
// PROBLEM: none

fun foo(): Boolean {
    listOf(1,2,3).find {
        <caret>return true
    }
    return false
}