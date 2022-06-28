// "Create class 'A'" "false"
// ACTION: Create object 'A'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: A
package p

fun foo() = p.<caret>A