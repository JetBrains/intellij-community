// "Create member function 'bar'" "false"
// ERROR: Unresolved reference: bar
fun <T> foo(t: T) {
    t.<caret>bar()
}