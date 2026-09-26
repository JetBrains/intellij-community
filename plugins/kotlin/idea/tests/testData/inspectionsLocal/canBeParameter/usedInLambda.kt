// PROBLEM: none
// WITH_STDLIB
class UsedInLambda(<caret>val x: Int) {
    init {
        run {
            val y = x
        }
    }
}