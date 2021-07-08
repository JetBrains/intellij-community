// PROBLEM: Condition is always false
// FIX: none
class X {
    var p: Int = 0

    fun test(x: X) {
        p = 1
        x.p = 2
        x.p -= 2
        if (<caret>p < x.p) {}
    }
}
