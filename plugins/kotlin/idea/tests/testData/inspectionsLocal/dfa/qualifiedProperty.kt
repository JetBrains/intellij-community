// PROBLEM: Condition is always true
// FIX: none
class X {
    var p: Int = 0

    fun test(x: X) {
        if (x.p > p) {
            if (<caret>x.p > p) {
            }
        }
    }
}
