// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int) {
    if (x + 1 > 5) {
        if (<caret>x < 3) {
        }
    }
}