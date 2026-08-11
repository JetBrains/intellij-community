// "Convert to anonymous object" "false"
// K2_AFTER_ERROR: INTERFACE_AS_FUNCTION
// K2_ERROR: INTERFACE_AS_FUNCTION

fun foo(): String = ""

interface I {
    fun foo(): String
}

fun test() {
    val i = I<caret> { foo() }
}