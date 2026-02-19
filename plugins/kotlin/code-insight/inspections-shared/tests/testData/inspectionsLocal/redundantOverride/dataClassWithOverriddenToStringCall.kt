// PROBLEM: none
sealed class Foo(open val i: Int) {
    override fun toString(): String = when (i) {
        1 -> "1"
        else -> "2"
    }
}

data class Bar(override val i: Int) : Foo(i) {
    <caret>override fun toString(): String = super.toString()
}
