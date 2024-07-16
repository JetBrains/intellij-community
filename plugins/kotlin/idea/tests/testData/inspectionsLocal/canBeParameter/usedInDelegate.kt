// PROBLEM: none
class UsedInDelegate(<caret>val x: Int) {
    val y: Int by lazy {
        x * x
    }
}