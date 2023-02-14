// ERROR: Unresolved reference: javaClass
// ERROR: Unresolved reference: javaClass

class Any

class With<caret>Constructor(x: Int, s: String) {
    val x: Int = 0
    val s: String = ""

    override fun hashCode(): Int = 1
}

// IGNORE_FIR