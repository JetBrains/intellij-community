// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

interface IF1 {
    fun af2() = <caret>foo
}