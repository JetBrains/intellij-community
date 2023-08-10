// "Create property 'foo'" "false"
// ACTION: Rename reference
// ACTION: Converts the assignment statement to an expression
// ERROR: Unresolved reference: foo

fun test() {
    J.<caret>foo = 1
}
