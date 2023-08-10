// "Create class 'A'" "false"
// ACTION: Introduce local variable
// ACTION: Put calls on separate lines
// ACTION: Rename reference
// ERROR: Unresolved reference: A
fun foo() = J.<caret>A.B
