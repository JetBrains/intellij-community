// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

interface A {
    val test: Int get() {
        return <caret>foo
    }
}