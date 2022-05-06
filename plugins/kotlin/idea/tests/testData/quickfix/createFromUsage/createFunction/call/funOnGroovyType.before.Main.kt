// "Create member function 'foo'" "false"
// ACTION: Create extension function 'A.foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

fun test(): Int {
    return A().<caret>foo()
}