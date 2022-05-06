// "Create extension function 'J.foo'" "false"
// ACTION: Create member function 'J.foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

fun test() {
    val a: Int = J.<caret>foo("1", 2)
}
