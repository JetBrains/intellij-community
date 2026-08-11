// PROBLEM: none
// ERROR: Unresolved reference: Base1
// K2_ERROR: UNRESOLVED_REFERENCE
class UsedInObjectSuper(<caret>val bar456: String) {
    fun foo() {
        object : Base1(bar456) {}
    }
}