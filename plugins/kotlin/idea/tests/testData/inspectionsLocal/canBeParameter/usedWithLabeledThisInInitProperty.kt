// PROBLEM: none
class UsedWithLabeledThisInInitProperty(<caret>val x: Int) {
    init {
        run {
            val y = this@UsedWithLabeledThisInInitProperty.x
        }
    }
}