// "Convert to anonymous object" "false"
// ERROR: Interface I does not have constructors
// ACTION: Introduce import alias
// ACTION: Split property declaration
// K2_ERROR: Interface 'interface I : Any' does not have constructors.
// K2_AFTER_ERROR: Interface 'interface I : Any' does not have constructors.
interface I {
    fun <T> foo(): String
}

fun test() {
    val i = <caret>I { "" }
}