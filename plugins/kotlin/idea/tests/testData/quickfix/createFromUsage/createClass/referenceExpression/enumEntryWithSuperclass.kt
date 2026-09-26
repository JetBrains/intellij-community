// "Create enum constant 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE
package p

fun foo(): X = E.<caret>A

enum class E {

}

open class X {

}