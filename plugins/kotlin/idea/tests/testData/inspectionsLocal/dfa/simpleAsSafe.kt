// PROBLEM: Condition is always false
// FIX: none
fun test(b: Boolean) {
    val x = if (b) "x" else 5
    val y = x as? String
    if (y == null) {
        if (<caret>b) {}
    }
}