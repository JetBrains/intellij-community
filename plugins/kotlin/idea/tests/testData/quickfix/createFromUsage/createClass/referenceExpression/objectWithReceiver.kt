// "Create object 'A'" "false"
// ACTION: Create extension property 'X.A'
// ACTION: Create member property 'X.A'
// ACTION: Create property 'A' as constructor parameter
// ACTION: Do not show return expression hints
// ACTION: Rename reference
// ERROR: Unresolved reference: A
package p

fun foo() = X().<caret>A

class X {

}