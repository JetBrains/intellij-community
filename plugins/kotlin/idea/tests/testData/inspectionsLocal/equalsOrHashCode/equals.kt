// ERROR: Unresolved reference: javaClass
// ERROR: Unresolved reference: javaClass
// K2-ERROR:
class With<caret>Constructor(x: Int, s: String) {
    val x: Int = 0
    val s: String = ""

    override fun hashCode(): Int = 1
}