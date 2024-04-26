// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

val test: Int get() {
    return <caret>foo
}