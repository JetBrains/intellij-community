// PROBLEM: none
// WITH_RUNTIME
abstract class X {
    abstract var x: Int

    fun test() {
        if (x > 5) {
            if (<caret>x < 3) {
            }
        }
    }
}
