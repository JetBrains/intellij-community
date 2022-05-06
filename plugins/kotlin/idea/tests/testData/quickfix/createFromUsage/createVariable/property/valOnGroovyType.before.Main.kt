// "Create property 'foo'" "false"
// ACTION: Create extension property 'A.foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

fun test(): String? {
    return A().<caret>foo
}