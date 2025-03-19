// PROBLEM: none
class UsedInGetter(<caret>val x: Int) {
    val y: Int
        get() = x
}