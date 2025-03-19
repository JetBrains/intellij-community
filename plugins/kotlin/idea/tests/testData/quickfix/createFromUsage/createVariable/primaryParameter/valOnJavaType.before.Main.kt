// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo

fun test(): String? {
    return A().<caret>foo
}