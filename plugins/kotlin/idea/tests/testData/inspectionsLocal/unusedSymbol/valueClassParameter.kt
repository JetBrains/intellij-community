// PROBLEM: none
// WITH_STDLIB
@JvmInline
value class V internal constructor(private val <caret>value: Int) {
    companion object {
        val Foo = V(0)
        val Bar = V(1)
        val Baz = V(2)
    }

    override fun toString() = when (this) {
        Foo -> "Foo"
        Bar -> "Bar"
        Baz -> "Baz"
        else -> ""
    }
}
