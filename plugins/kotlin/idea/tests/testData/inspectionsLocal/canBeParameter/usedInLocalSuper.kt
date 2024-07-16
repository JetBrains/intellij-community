// PROBLEM: none
class UsedInLocalSuper(<caret>val bar456: String) {
    fun foo() {
        class Local : Base1(bar456)
    }
}