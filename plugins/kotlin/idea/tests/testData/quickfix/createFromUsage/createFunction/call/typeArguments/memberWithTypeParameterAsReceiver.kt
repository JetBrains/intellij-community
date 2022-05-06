// "Create member function 'bar'" "false"
// ACTION: Convert to run
// ACTION: Convert to with
// ACTION: Create extension function 'T.bar'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: bar
fun <T> foo(t: T) {
    t.<caret>bar()
}