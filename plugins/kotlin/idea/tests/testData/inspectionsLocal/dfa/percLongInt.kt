// PROBLEM: Condition is always true
// FIX: none
fun test(x: Long, y: Int) {
    val z = 0
    x % y
    if (<caret>z == 0) {}
}