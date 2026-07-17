// IS_APPLICABLE: false
// ERROR: Unresolved reference: foo
// K2_ERROR: UNRESOLVED_REFERENCE
fun bar() {
    foo(<caret>b = true)
}