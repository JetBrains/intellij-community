// PROBLEM: none
class UsedInLambda(<caret>val x: Int) {
    init {
        run {
            val y = x
        }
    }
}