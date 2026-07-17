// IS_APPLICABLE: false
// ERROR: Unresolved reference: foo
// K2_ERROR: UNRESOLVED_REFERENCE
fun bar() {
    <caret>foo("", true)
}