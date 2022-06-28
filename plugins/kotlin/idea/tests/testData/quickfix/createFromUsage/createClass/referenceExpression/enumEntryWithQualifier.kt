// "Create enum constant 'A'" "false"
// ACTION: Create extension property 'X.Companion.A'
// ACTION: Create member property 'X.Companion.A'
// ACTION: Create object 'A'
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: A
package p

fun foo() = X.<caret>A

class X {

}