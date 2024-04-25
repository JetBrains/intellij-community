// FIX: Remove 'val' from parameter
class UsedInInitializer(<caret>val x: Int) {
    val y: Int
    init {
        y = x
    }
}