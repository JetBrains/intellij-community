// "Create object 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

fun foo() = X().<caret>A

class X {

}