// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// WITH_STDLIB
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
fun refer() {
    1 <caret>foo 2
}