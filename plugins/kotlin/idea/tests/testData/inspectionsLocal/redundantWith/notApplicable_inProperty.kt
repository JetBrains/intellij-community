// PROBLEM: none
// WITH_STDLIB
fun test() {
    val i = <caret>with ("") {
        println()
        1
    }
}