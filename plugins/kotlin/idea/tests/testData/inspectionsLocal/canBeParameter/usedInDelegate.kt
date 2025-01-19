// PROBLEM: none
// WITH_STDLIB
class UsedInDelegate(<caret>val x: Int) {
    val y: Int by lazy {
        x * x
    }
}