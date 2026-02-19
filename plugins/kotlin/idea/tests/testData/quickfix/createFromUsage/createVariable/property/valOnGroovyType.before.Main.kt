// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo

fun test(): String? {
    return A().<caret>foo
}