// PROBLEM: Condition is always true
// FIX: none
fun test() {
    val x = 10
    val y = -x
    if (<caret>y == -10) {}
}