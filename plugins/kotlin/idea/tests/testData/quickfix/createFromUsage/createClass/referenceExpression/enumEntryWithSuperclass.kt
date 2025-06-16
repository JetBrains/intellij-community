// "Create enum constant 'A'" "false"
// ERROR: Unresolved reference: A
// K2_AFTER_ERROR: Unresolved reference 'A'.
package p

fun foo(): X = E.<caret>A

enum class E {

}

open class X {

}