// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int) {
    val y = 100 / x
    if (<caret>x == 0) {

    }
    if (x == 1) {}
}