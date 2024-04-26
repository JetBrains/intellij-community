// "Create property 'foo' as constructor parameter" "false"
// ERROR: Unresolved reference: foo

fun test() {
    fun nestedTest(): Int {
        return <caret>foo
    }
}
