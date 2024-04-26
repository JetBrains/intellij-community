// "Create property 'foo'" "false"
// ERROR: Unresolved reference: foo

fun test() {
    fun nestedTest(): Int {
        return <caret>foo
    }
}
