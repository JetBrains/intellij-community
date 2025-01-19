// PROBLEM: none
// ERROR: Unresolved reference: Base1
// K2-ERROR: Unresolved reference 'Base1'.
class UsedInLocalSuper(<caret>val bar456: String) {
    fun foo() {
        class Local : Base1(bar456)
    }
}