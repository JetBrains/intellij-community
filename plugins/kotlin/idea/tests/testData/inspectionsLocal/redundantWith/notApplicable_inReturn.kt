// PROBLEM: none
// WITH_STDLIB
fun test(): Int {
    return <caret>with ("") {
        println()
        1
    }
}