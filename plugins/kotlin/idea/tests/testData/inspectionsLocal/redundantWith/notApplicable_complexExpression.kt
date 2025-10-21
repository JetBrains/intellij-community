// PROBLEM: none
// WITH_STDLIB
fun foo(): Int = 10

fun test() {
    <caret>with(foo()) {
        toString()
    }
}