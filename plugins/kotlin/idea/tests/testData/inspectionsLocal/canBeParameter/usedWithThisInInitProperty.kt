// PROBLEM: none
class UsedWithThisInInitProperty(<caret>val x: Int) {
    init {
        val y = this.x
    }
}