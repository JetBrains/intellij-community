// WITH_STDLIB
// FIX: Replace with loop over elements
class CustomIterable<T>(private val elements: List<T>) : Iterable<T> {
    val size: Int get() = elements.size
    operator fun get(index: Int): T = elements[index]
    override fun iterator(): Iterator<T> = elements.iterator()
}

fun test(iterable: CustomIterable<String>) {
    for (i in 0 until <caret>iterable.size) {
        println(iterable[i])
    }
}
