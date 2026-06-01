// "Create member function 'bar'" "false"
// ERROR: Unresolved reference: bar
// K2_ERROR: Unresolved reference 'bar'.
// K2_AFTER_ERROR: Unresolved reference 'bar'.
fun <T> foo(t: T) {
    t.<caret>bar()
}