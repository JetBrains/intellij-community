// WITH_STDLIB
// PROBLEM: none
class CustomIterable<T>(private val elements: List<T>) : Iterable<T> {
    val size: Int get() = elements.size
    operator fun get(index: Int): T = elements[index]
    override fun iterator(): Iterator<T> = elements.iterator()
}

fun test(iterable: CustomIterable<String>) {
    // Index is not used for element access, only INDICES_ONLY pattern applies
    // But custom Iterable doesn't have .indices, so no fix should be available
    for (i in 0 until <caret>iterable.size) {
        doSomething(i)
    }
}

fun doSomething(i: Int) {}
