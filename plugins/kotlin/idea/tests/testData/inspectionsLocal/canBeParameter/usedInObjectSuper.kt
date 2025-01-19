// PROBLEM: none
// ERROR: Unresolved reference: Base1
// K2-ERROR: Unresolved reference 'Base1'.
class UsedInObjectSuper(<caret>val bar456: String) {
    fun foo() {
        object : Base1(bar456) {}
    }
}