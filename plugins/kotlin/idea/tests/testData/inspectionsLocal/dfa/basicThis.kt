// PROBLEM: Condition is always true
// FIX: none
class X {
    fun test() {
        val obj : Any = this
        if (<caret>obj is X) {}
    }
}