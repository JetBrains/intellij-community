// ERROR: Unresolved reference: javaClass
// ERROR: Unresolved reference: javaClass

open class Base<T>(t: T) {
    override fun hashCode(): Int = 0
    override fun equals(foo: Any?) = false
}

class With<caret>Constructor(x: Int, s: String) : Base<Int>(3) {
    val x: Int = 0
    val s: String = ""

    override fun hashCode(): Int = 1
}