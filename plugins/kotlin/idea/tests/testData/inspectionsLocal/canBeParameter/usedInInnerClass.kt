// PROBLEM: none
class UsedInInnerClass(<caret>val x: Int) {
    inner class Inner {
        fun foo() = x
    }
}