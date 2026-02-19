// PROBLEM: none
class UsedInObjectLiteral(<caret>val x: Int) {
    val y = object: Any() {
        fun bar() = x
    }
}