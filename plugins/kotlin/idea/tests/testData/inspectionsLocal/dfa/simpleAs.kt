// PROBLEM: Condition is always true
// FIX: none
fun test(b: Boolean) {
    val x = if (b) "x" else 5
    val y = x as String
    if (<caret>b) {}
}