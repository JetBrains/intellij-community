// ERROR: Unresolved reference: javaClass
// ERROR: Unresolved reference: javaClass

class With<caret>Constructor(x: Int, s: String) {
    val x: Int = 0
    val s: String = ""

    class Boolean

    override fun hashCode(): Int = 1
}

// IGNORE_FIR