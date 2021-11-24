// PROBLEM: none
// WITH_STDLIB
fun test() {
    <caret>with ("") {
        return@with
    }
}