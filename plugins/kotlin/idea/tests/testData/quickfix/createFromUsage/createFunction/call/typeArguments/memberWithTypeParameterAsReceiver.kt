// "Create member function 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
fun <T> foo(t: T) {
    t.<caret>bar()
}