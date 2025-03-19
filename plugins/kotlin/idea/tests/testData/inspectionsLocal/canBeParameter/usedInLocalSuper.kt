// PROBLEM: none
// ERROR: Unresolved reference: Base1
// K2_ERROR: Unresolved reference 'Base1'.
class UsedInLocalSuper(<caret>val bar456: String) {
    fun foo() {
        class Local : Base1(bar456)
    }
}