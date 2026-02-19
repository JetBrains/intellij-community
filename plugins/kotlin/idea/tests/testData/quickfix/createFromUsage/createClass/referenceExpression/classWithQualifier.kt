// "Create class 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: Unresolved reference 'A'.
package p

fun foo() = X.<caret>A

class X {

}