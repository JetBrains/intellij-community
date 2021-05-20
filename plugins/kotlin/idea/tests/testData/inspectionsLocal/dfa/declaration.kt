// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int) {
    var y = x
    if (y <caret>!= x) {}
}