// PROBLEM: none
class UsedInLocalFunction(<caret>val x: Int) {
    init {
        fun local() {
            val y = x
        }
        local()
    }
}