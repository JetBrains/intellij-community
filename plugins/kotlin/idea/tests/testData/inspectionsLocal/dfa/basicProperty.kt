// PROBLEM: Condition is always false
// FIX: none
class X {
    var x: Int = 0

    fun test() {
        if (x > 5) {
            if (<caret>x < 3) {
            }
        }
    }
}
