// "Create parameter 'foo'" "false"
// ACTION: Converts the assignment statement to an expression
// ACTION: Create local variable 'foo'
// ACTION: Create property 'foo'
// ACTION: Rename reference
// ERROR: Unresolved reference: foo

fun test(n: Int) {
    <caret>foo = n + 1
}