// "Create class 'A'" "false"
// ERROR: Unresolved reference: A
// K2_ERROR: Unresolved reference 'A' on receiver of type 'X'.
// K2_AFTER_ERROR: Unresolved reference 'A' on receiver of type 'X'.
package p

fun foo() = X().<caret>A

class X {

}