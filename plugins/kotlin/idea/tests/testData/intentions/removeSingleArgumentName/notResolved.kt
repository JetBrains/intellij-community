// IS_APPLICABLE: false
// ERROR: Unresolved reference: foo
// K2_ERROR: Unresolved reference 'foo'.
fun bar() {
    foo(<caret>b = true)
}