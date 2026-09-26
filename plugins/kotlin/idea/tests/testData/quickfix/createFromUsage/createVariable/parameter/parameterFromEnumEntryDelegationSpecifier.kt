// "Create parameter 'x'" "false"
// ERROR: Unresolved reference: x
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

enum class E(n: Int) {
    X(<caret>x)
}