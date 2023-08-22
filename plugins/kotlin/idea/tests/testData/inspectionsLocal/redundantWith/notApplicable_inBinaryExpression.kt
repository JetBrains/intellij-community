// PROBLEM: none
// WITH_STDLIB
fun test() {
    val b = 1 == <caret>with ("") {
        println()
        1
    }
}