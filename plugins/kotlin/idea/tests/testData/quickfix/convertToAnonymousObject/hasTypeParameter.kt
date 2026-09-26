// "Convert to anonymous object" "false"
// ERROR: Interface I does not have constructors
// ACTION: Introduce import alias
// ACTION: Split property declaration
// K2_AFTER_ERROR: INTERFACE_AS_FUNCTION
// K2_ERROR: INTERFACE_AS_FUNCTION
interface I {
    fun <T> foo(): String
}

fun test() {
    val i = <caret>I { "" }
}