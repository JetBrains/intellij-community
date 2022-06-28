// "Create property 'foo'" "false"
// ACTION: Rename reference
// ACTION: Do not show return expression hints
// ACTION: Converts the assignment statement to an expression
// ERROR: Unresolved reference: foo

fun test() {
    J.<caret>foo = 1
}
