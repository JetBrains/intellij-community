// "Convert to anonymous object" "false"
// ACTION: Introduce import alias
// ERROR: Interface I does not have constructors
// K2_AFTER_ERROR: INTERFACE_AS_FUNCTION
// K2_ERROR: INTERFACE_AS_FUNCTION
interface I {
    fun foo(): String
    fun bar(): Unit
}

fun test() {
    <caret>I {
    }
}