// "Convert to anonymous object" "false"
// K2_ERROR: Interface 'interface I : Any' does not have constructors.
// K2_AFTER_ERROR: Interface 'interface I : Any' does not have constructors.

fun foo(): String = ""

interface I {
    fun foo(): String
}

fun test() {
    val i = I<caret> { foo() }
}