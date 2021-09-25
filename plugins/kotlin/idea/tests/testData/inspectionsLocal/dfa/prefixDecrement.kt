// PROBLEM: Condition is always true
// FIX: none
fun test() {
    var x = 10
    --x
    if (<caret>x == 9) {}
}