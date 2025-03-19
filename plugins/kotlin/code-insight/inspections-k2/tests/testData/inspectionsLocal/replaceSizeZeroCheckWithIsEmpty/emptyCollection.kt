// PROBLEM: none
// WITH_STDLIB

class EmptyCollection<T> : AbstractCollection<T>() {

    override val size: Int
        get() = 0

    override fun iterator(): Iterator<T> =
        emptyList<T>().iterator()

    override fun isEmpty(): Boolean =
        size == <caret> 0
}
