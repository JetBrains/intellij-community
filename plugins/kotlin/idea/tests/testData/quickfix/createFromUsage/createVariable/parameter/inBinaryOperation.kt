// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo
// WITH_STDLIB
fun refer() {
    1 <caret>foo 2
}