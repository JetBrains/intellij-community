// "Create parameter 'b'" "false"
// ERROR: Unresolved reference: b
// K2_AFTER_ERROR: Unresolved reference 'b'.

open class A(val a: Int) {

}

object B: A(<caret>b) {

}