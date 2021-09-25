// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int?) {
    if (x != null && x > 5 && <caret>x < 3) {}
}