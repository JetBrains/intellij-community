// ERROR: Unresolved reference: javaClass
// ERROR: Unresolved reference: javaClass

// IGNORE_FIR

open class Any {
    override fun hashCode(): Int = 0
}

class With<caret>Constructor(x: Int, s: String) : Any() {
    val x: Int = 0
    val s: String = ""

    override fun hashCode(): Int = 1
}