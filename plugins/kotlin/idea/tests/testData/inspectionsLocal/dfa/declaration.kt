// PROBLEM: Condition is always false
// FIX: none
fun test(x : Int) {
    var y = x
    var b = false
    if (y <caret>!= x) {}
}