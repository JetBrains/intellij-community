// PROBLEM: Condition is always false
// FIX: none
fun test(x: Int) = when {
    x > 10 -> 10
    <caret>x > 15 -> 15
    else -> 0
}
