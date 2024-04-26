// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo

internal fun test(a: A): String? {
    return a.<caret>foo
}