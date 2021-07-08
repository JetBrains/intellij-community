// PROBLEM: none
// WITH_RUNTIME
class X {
    @Volatile
    var x: Int = 0

    fun test() {
        if (x > 5) {
            if (<caret>x < 3) {
            }
        }
    }
}
