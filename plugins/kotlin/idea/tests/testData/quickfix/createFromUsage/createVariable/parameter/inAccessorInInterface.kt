// "Create parameter 'foo'" "false"
// ERROR: Unresolved reference: foo

interface A {
    val test: Int get() {
        return <caret>foo
    }
}