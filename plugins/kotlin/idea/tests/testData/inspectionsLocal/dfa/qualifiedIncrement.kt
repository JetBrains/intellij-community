// PROBLEM: Condition is always false
// FIX: none
fun test(x : X) {
    if (x.x == 0) return
    x.x++
    if (<caret>x.x == 1) return
}
data class X(var x: Int)