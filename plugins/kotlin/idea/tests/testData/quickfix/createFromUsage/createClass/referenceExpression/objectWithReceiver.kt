// "Create object 'A'" "false"
// ERROR: Unresolved reference: A
package p

fun foo() = X().<caret>A

class X {

}