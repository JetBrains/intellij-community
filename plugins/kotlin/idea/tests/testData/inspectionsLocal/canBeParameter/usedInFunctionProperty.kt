// PROBLEM: none
class UsedInFunctionProperty(<caret>val x: Int) {
    fun get(): Int {
        val y = x
        return y
    }
}