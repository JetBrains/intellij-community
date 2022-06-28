// "Create property 'foo'" "false"
// ACTION: Create local variable 'foo'
// ACTION: Create parameter 'foo'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

fun test() {
    fun nestedTest(): Int {
        return <caret>foo
    }
}
