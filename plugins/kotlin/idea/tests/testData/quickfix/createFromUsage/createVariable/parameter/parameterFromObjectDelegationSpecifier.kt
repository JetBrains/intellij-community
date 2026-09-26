// "Create parameter 'b'" "false"
// ERROR: Unresolved reference: b
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: UNRESOLVED_REFERENCE

open class A(val a: Int) {

}

object B: A(<caret>b) {

}