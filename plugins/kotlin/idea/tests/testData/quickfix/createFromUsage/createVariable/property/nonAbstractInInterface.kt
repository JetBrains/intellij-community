// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: Unresolved reference 'foo'.

interface IF1 {
    fun af2() = <caret>foo
}