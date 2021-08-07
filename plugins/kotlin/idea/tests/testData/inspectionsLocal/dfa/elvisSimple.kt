// PROBLEM: Condition is always true
// FIX: none
fun test(x: Int?) {
    val y = x ?: 2
    if (x == null) {
        if (<caret>y == 2) {

        }
    }
}