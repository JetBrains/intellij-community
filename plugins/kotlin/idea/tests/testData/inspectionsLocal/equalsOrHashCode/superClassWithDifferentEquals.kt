// WITH_STDLIB
open class Base() {
    override fun hashCode(): Int = 0
    fun equals(base: Base?) = false
}

class With<caret>Constructor(x: Int, s: String) : Base() {
    val x: Int = 0
    val s: String = ""

    override fun hashCode(): Int = 1
}