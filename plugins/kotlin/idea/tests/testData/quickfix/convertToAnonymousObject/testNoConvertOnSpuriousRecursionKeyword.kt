// "Convert to anonymous object" "false"
// K2_AFTER_ERROR: INTERFACE_AS_FUNCTION
// K2_ERROR: INTERFACE_AS_FUNCTION

fun `when`(): String = ""

interface I {
    fun `when`(): String
}

fun test() {
    val i = I<caret> { `when`() }
}