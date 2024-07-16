// PROBLEM: none
class UsedInObjectSuper(<caret>val bar456: String) {
    fun foo() {
        object : Base1(bar456) {}
    }
}