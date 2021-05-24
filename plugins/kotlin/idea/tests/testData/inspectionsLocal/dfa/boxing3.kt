// PROBLEM: Condition is always false
// FIX: none
fun test(y : Int) {
    var x : Int?
    x = y
    if (<caret>x == null) {}
}