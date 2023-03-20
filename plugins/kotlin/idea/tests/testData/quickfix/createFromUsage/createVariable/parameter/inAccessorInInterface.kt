// "Create parameter 'foo'" "false"
// ACTION: Create abstract property 'foo'
// ACTION: Create local variable 'foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

interface A {
    val test: Int get() {
        return <caret>foo
    }
}