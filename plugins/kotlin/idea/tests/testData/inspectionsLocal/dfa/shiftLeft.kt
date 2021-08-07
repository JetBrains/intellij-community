// PROBLEM: Condition is always false
// FIX: none
fun test(x: Int) {
    val y = x shl 2
    if (<caret>y % 4 == 2) {}
}