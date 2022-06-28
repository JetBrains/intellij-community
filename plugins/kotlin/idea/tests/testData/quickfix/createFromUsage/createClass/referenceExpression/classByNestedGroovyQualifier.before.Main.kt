// "Create class 'A'" "false"
// ACTION: Do not show return expression hints
// ACTION: Introduce local variable
// ACTION: Rename reference
// ERROR: Unresolved reference: A
fun foo() = J.<caret>A.B
