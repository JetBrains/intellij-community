// FIX: Remove 'val' from parameter
class UsedWithoutThisInInitProperty(<caret>val x: Int) {
    init {
        val y = x
    }
}