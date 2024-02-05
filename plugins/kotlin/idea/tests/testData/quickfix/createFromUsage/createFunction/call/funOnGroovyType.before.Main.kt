// "Create member function 'foo'" "false"
// ERROR: Unresolved reference: foo

fun test(): Int {
    return A().<caret>foo()
}