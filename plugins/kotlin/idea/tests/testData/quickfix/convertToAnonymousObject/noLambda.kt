// "Convert to anonymous object" "false"
// ACTION: Introduce import alias
// ACTION: Split property declaration
// ERROR: Interface I does not have constructors
// K2_AFTER_ERROR: INTERFACE_AS_FUNCTION
// K2_ERROR: INTERFACE_AS_FUNCTION
interface I {
    fun foo(): String
}

fun test() {
    val i = <caret>I()
}