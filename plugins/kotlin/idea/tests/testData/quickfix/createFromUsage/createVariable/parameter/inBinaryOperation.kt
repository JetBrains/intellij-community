// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// WITH_STDLIB
// K2_AFTER_ERROR: Unresolved reference 'foo'.
fun refer() {
    1 <caret>foo 2
}