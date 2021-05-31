// PROBLEM: Condition is always false
// FIX: none
fun test(x: Int) {
    if (x < 0) return
    var y = x
    ++y
    if (<caret>y == 0) {}
}