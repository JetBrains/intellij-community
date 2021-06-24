// "Create parameter 'foo'" "false"
// ACTION: Create local variable 'foo'
// ACTION: Create property 'foo'
// ACTION: Rename reference
// ACTION: Converts the assignment statement to an expression
// ERROR: Unresolved reference: foo

fun test(n: Int) {
    <caret>foo = n + 1
}